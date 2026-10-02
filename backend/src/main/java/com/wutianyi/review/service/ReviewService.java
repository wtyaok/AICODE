package com.wutianyi.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wutianyi.review.api.Contracts.*;
import com.wutianyi.review.config.ReviewConfig;
import com.wutianyi.review.model.*;
import com.wutianyi.review.repo.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {
    private final TaskRepository tasks;
    private final FindingRepository findings;
    private final FeedbackRepository feedback;
    private final RuleRepository rules;
    private final StringRedisTemplate redis;
    private final ReviewConfig config;
    private final ReviewWorker worker;

    public ReviewService(
        TaskRepository tasks,
        FindingRepository findings,
        FeedbackRepository feedback,
        RuleRepository rules,
        StringRedisTemplate redis,
        ReviewConfig config,
        ReviewWorker worker
    ) {
        this.tasks = tasks;
        this.findings = findings;
        this.feedback = feedback;
        this.rules = rules;
        this.redis = redis;
        this.config = config;
        this.worker = worker;
    }

    public ReviewTask createManual(ReviewRequest request, String requestKey) {
        boolean hasDiff = request.diff() != null && !request.diff().isBlank();
        boolean hasMr = request.projectId() != null && !request.projectId().isBlank()
            && request.mrIid() != null && request.mrIid() > 0;

        if (!hasDiff && !hasMr) {
            throw new IllegalArgumentException("必须提供 diff，或提供项目 ID 和 MR IID");
        }
        if (hasDiff && request.diff().length() > config.maxDiffChars) {
            throw new IllegalArgumentException("diff 超过最大长度");
        }

        String key = requestKey == null || requestKey.isBlank()
            ? UUID.randomUUID().toString() : "manual:" + requestKey;
        ReviewTask task = new ReviewTask();
        task.idempotencyKey = digest(key);
        task.title = request.title() == null || request.title().isBlank()
            ? "手动代码评审" : request.title().trim();
        task.projectId = request.projectId();
        task.mrIid = request.mrIid();
        task.manualDiff = hasDiff ? request.diff() : null;
        task.postComment = request.postComment() && hasMr;
        task.triggerType = "manual";
        return submit(task);
    }

    public ReviewTask createWebhook(JsonNode event) {
        String kind = event.path("object_kind").asText();
        if ("push".equals(kind)) {
            return createPushWebhook(event);
        }
        if (!"merge_request".equals(kind)) {
            throw new IllegalArgumentException("只处理 Push 和 Merge Request 事件");
        }

        JsonNode attributes = event.path("object_attributes");
        String action = attributes.path("action").asText("");
        if (!List.of("open", "update", "reopen").contains(action)) {
            throw new IllegalArgumentException("已忽略 MR 动作：" + action);
        }

        String projectId = event.path("project").path("id").asText("");
        long iid = attributes.path("iid").asLong(0);
        String sha = attributes.path("last_commit").path("id").asText("");
        if (projectId.isBlank() || iid <= 0 || sha.isBlank()) {
            throw new IllegalArgumentException("Webhook 缺少 project.id、iid 或 commit SHA");
        }

        ReviewTask task = new ReviewTask();
        task.idempotencyKey = digest(projectId + ":" + iid + ":" + sha);
        task.projectId = projectId;
        task.mrIid = iid;
        task.commitSha = sha;
        task.title = attributes.path("title").asText("MR !" + iid);
        task.triggerType = "webhook";
        task.postComment = config.commentEnabled;
        return submit(task);
    }

    private ReviewTask createPushWebhook(JsonNode event) {
        String projectId = event.path("project").path("id").asText("");
        String baseSha = event.path("before").asText("");
        String commitSha = event.path("after").asText("");
        String ref = event.path("ref").asText("");
        if (projectId.isBlank() || baseSha.isBlank() || commitSha.isBlank()
            || ref.isBlank() || baseSha.matches("0+") || commitSha.matches("0+")
            || baseSha.equals(commitSha)) {
            throw new IllegalArgumentException("Push 事件缺少可比较的提交范围");
        }

        ReviewTask task = new ReviewTask();
        task.idempotencyKey = digest(projectId + ":" + ref + ":" + commitSha);
        task.projectId = projectId;
        task.baseSha = baseSha;
        task.commitSha = commitSha;
        task.ref = ref;
        task.title = "Push " + ref.replace("refs/heads/", "") + " @ "
            + commitSha.substring(0, Math.min(8, commitSha.length()));
        task.triggerType = "push";
        task.postComment = false;
        return submit(task);
    }

    private ReviewTask submit(ReviewTask task) {
        var existing = tasks.findByIdempotencyKey(task.idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            ReviewTask saved = tasks.saveAndFlush(task);
            cacheStatus(saved);
            worker.process(saved.id);
            return saved;
        } catch (DataIntegrityViolationException duplicate) {
            return tasks.findByIdempotencyKey(task.idempotencyKey)
                .orElseThrow(() -> duplicate);
        }
    }

    public Page<TaskSummary> list(String query, String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page 必须非负，size 必须在 1 到 100 之间");
        }
        return tasks.search(
            query == null ? "" : query.trim(),
            status == null ? "" : status.trim(),
            PageRequest.of(page, size, Sort.by("createdAt").descending())
        ).map(TaskSummary::from);
    }

    public TaskDetail detail(String id) {
        ReviewTask task = tasks.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        return new TaskDetail(
            task,
            findings.findByTaskIdOrderByIdAsc(id),
            feedback.findByTaskIdOrderByCreatedAtDesc(id)
        );
    }

    public java.util.Map<String, Object> stats() {
        java.util.Map<String, Long> severities = new java.util.LinkedHashMap<>();
        for (String level : List.of("critical", "high", "medium", "low")) {
            severities.put(level, findings.countBySeverity(level));
        }
        return java.util.Map.of(
            "tasks", tasks.count(),
            "findings", findings.count(),
            "open", findings.countByFeedbackStatus("open"),
            "severities", severities
        );
    }

    @Transactional
    public Feedback addFeedback(long findingId, FeedbackRequest request) {
        if (!List.of("accepted", "ignored", "false_positive").contains(request.status())) {
            throw new IllegalArgumentException("反馈状态无效");
        }
        Finding finding = findings.findById(findingId)
            .orElseThrow(() -> new IllegalArgumentException("问题不存在"));
        finding.feedbackStatus = request.status();
        findings.save(finding);
        Feedback item = new Feedback();
        item.findingId = finding.id;
        item.taskId = finding.taskId;
        item.status = request.status();
        item.commentText = request.comment();
        return feedback.save(item);
    }

    public List<ReviewRule> listRules() {
        return rules.findAll(Sort.by("id"));
    }

    public ReviewRule saveRule(String id, RuleRequest request) {
        if (id == null || !id.matches("[a-z0-9-]{2,40}")) {
            throw new IllegalArgumentException("规则 ID 只能包含小写字母、数字和连字符");
        }
        if (!List.of("critical", "high", "medium", "low").contains(request.severity())) {
            throw new IllegalArgumentException("严重等级无效");
        }
        if (request.name() == null || request.name().isBlank()
            || request.suggestion() == null || request.suggestion().isBlank()
            || request.pattern() == null || request.pattern().isBlank()) {
            throw new IllegalArgumentException("规则名称、表达式和建议不能为空");
        }
        try {
            java.util.regex.Pattern.compile(request.pattern());
        } catch (Exception error) {
            throw new IllegalArgumentException("规则正则表达式无效");
        }
        ReviewRule rule = rules.findById(id).orElseGet(ReviewRule::new);
        rule.id = id;
        rule.name = request.name();
        rule.severity = request.severity();
        rule.pattern = request.pattern();
        rule.suggestion = request.suggestion();
        rule.enabled = request.enabled();
        return rules.save(rule);
    }

    public void cacheStatus(ReviewTask task) {
        try {
            redis.opsForValue().set(
                "review:status:" + task.id, task.status, Duration.ofHours(24)
            );
        } catch (Exception ignored) {
            // MySQL remains the source of truth when Redis is unavailable.
        }
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
