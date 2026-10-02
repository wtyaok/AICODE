package com.wutianyi.review.api;

import com.wutianyi.review.model.*;
import java.time.Instant;
import java.util.List;

public final class Contracts {
    private Contracts() {}

    public record ReviewRequest(
        String title,
        String projectId,
        Long mrIid,
        String diff,
        boolean postComment
    ) {}

    public record FeedbackRequest(String status, String comment) {}

    public record RuleRequest(
        String name,
        String severity,
        String pattern,
        String suggestion,
        boolean enabled
    ) {}

    public record DiffFile(String path, String oldPath, String diff) {}

    public record RulePayload(
        String id,
        String name,
        String severity,
        String pattern,
        String suggestion
    ) {
        public static RulePayload from(ReviewRule rule) {
            return new RulePayload(
                rule.id, rule.name, rule.severity, rule.pattern, rule.suggestion
            );
        }
    }

    public record AnalyzerRequest(
        String diff,
        List<DiffFile> files,
        List<RulePayload> rules
    ) {}

    public record FindingPayload(
        String file,
        Integer line,
        String ruleId,
        String category,
        String severity,
        String message,
        String suggestion,
        String source
    ) {}

    public record AnalyzerResponse(
        List<FindingPayload> findings,
        String llmError
    ) {}

    public record TaskSummary(
        String id,
        String title,
        String projectId,
        Long mrIid,
        String commitSha,
        String status,
        Integer findingCount,
        String commentStatus,
        Instant createdAt,
        Instant finishedAt,
        String errorMessage
    ) {
        public static TaskSummary from(ReviewTask task) {
            return new TaskSummary(
                task.id, task.title, task.projectId, task.mrIid,
                task.commitSha, task.status, task.findingCount,
                task.commentStatus, task.createdAt, task.finishedAt,
                task.errorMessage
            );
        }
    }

    public record TaskDetail(
        ReviewTask task,
        List<Finding> findings,
        List<Feedback> feedback
    ) {}
}
