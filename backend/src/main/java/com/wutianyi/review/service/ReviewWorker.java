package com.wutianyi.review.service;

import com.wutianyi.review.api.Contracts.*;
import com.wutianyi.review.config.ReviewConfig;
import com.wutianyi.review.model.*;
import com.wutianyi.review.repo.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReviewWorker {
    private final TaskRepository tasks;
    private final FindingRepository findings;
    private final RuleRepository rules;
    private final GitlabClient gitlab;
    private final AnalyzerClient analyzer;
    private final StringRedisTemplate redis;
    private final ReviewConfig config;

    public ReviewWorker(
        TaskRepository tasks,
        FindingRepository findings,
        RuleRepository rules,
        GitlabClient gitlab,
        AnalyzerClient analyzer,
        StringRedisTemplate redis,
        ReviewConfig config
    ) {
        this.tasks = tasks;
        this.findings = findings;
        this.rules = rules;
        this.gitlab = gitlab;
        this.analyzer = analyzer;
        this.redis = redis;
        this.config = config;
    }

    @Async("taskExecutor")
    public void process(String taskId) {
        ReviewTask task = tasks.findById(taskId).orElse(null);
        if (task == null) {
            return;
        }

        task.status = "processing";
        task.startedAt = Instant.now();
        saveStatus(task);

        try {
            String diff = task.manualDiff;
            List<DiffFile> files = List.of();

            if (diff == null || diff.isBlank()) {
                if ("push".equals(task.triggerType)) {
                    files = gitlab.fetchComparison(
                        task.projectId, task.baseSha, task.commitSha
                    );
                } else {
                    GitlabClient.MergeRequest mr = gitlab.fetchMergeRequest(
                        task.projectId, task.mrIid
                    );
                    if ("webhook".equals(task.triggerType)
                        && !task.commitSha.equals(mr.sha())) {
                        task.status = "skipped";
                        task.errorMessage = "MR 已更新到新提交，本次旧 Webhook 不再评审";
                        task.finishedAt = Instant.now();
                        saveStatus(task);
                        return;
                    }
                    task.commitSha = mr.sha();
                    task.title = mr.title();
                    files = mr.files();
                }
                diff = "";
            }

            int length = diff.length();
            for (DiffFile file : files) {
                length += file.diff().length();
            }
            if (length == 0) {
                throw new IllegalArgumentException("MR 没有可评审的文本变更");
            }
            if (length > config.maxDiffChars) {
                throw new IllegalArgumentException("diff 超过最大长度，请缩小 MR");
            }

            List<RulePayload> activeRules = rules.findByEnabledTrue()
                .stream().map(RulePayload::from).toList();
            AnalyzerResponse result = analyzer.analyze(
                new AnalyzerRequest(diff, files, activeRules)
            );
            List<Finding> savedFindings = new ArrayList<>();

            for (FindingPayload item : result.findings()) {
                Finding finding = new Finding();
                finding.taskId = task.id;
                finding.filePath = item.file();
                finding.lineNumber = item.line();
                finding.ruleId = item.ruleId();
                finding.category = item.category();
                finding.severity = item.severity();
                finding.message = item.message();
                finding.suggestion = item.suggestion();
                finding.source = item.source();
                savedFindings.add(finding);
            }
            savedFindings = findings.saveAll(savedFindings);
            task.findingCount = savedFindings.size();
            task.status = "completed";
            task.finishedAt = Instant.now();
            task.llmError = result.llmError();
            saveStatus(task);

            if (task.postComment && task.projectId != null && task.mrIid != null) {
                postComment(task, savedFindings);
            }
        } catch (Exception error) {
            task.status = "failed";
            task.errorMessage = abbreviate(error.getMessage());
            task.finishedAt = Instant.now();
            saveStatus(task);
        }
    }

    private void postComment(ReviewTask task, List<Finding> savedFindings) {
        try {
            task.commentStatus = "posting";
            tasks.save(task);
            long noteId = gitlab.postComment(
                task.projectId,
                task.mrIid,
                formatComment(task, savedFindings)
            );
            task.commentId = noteId;
            task.commentStatus = "posted";
            task.commentError = null;
        } catch (Exception error) {
            task.commentStatus = "failed";
            task.commentError = abbreviate(error.getMessage());
        }
        tasks.save(task);
    }

    private String formatComment(ReviewTask task, List<Finding> items) {
        StringBuilder body = new StringBuilder();
        body.append("## AI 代码评审\n\n");
        body.append("<!-- review-task:").append(task.id).append(" -->\n");
        body.append("提交：").append(task.commitSha).append("\n\n");

        if (items.isEmpty()) {
            body.append("未发现规则命中或可确认的问题。");
        } else {
            int index = 1;
            for (Finding item : items) {
                body.append(index++).append(". **[")
                    .append(item.severity).append("] ")
                    .append(item.category).append("**  ")
                    .append(item.filePath).append(":")
                    .append(item.lineNumber == null ? "-" : item.lineNumber)
                    .append("\n   - ").append(item.message)
                    .append("\n   - 建议：").append(item.suggestion).append("\n");
            }
        }
        return body.toString();
    }

    private void saveStatus(ReviewTask task) {
        tasks.save(task);
        try {
            redis.opsForValue().set(
                "review:status:" + task.id,
                task.status,
                Duration.ofHours(24)
            );
        } catch (Exception ignored) {
            // MySQL remains authoritative when Redis is unavailable.
        }
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "未知错误";
        }
        return value.substring(0, Math.min(value.length(), 1000));
    }
}
