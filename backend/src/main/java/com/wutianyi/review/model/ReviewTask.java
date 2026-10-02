package com.wutianyi.review.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_tasks", uniqueConstraints = {
    @UniqueConstraint(name = "uk_review_idempotency", columnNames = "idempotency_key")
})
public class ReviewTask {
    @Id
    public String id = UUID.randomUUID().toString();

    @Column(name = "idempotency_key", nullable = false, length = 180)
    public String idempotencyKey;

    @Column(nullable = false)
    public String title;

    public String projectId;
    public Long mrIid;
    public String baseSha;
    public String commitSha;
    public String ref;
    public String triggerType;
    public String status = "queued";
    public Integer findingCount = 0;
    public Long commentId;
    public String commentStatus = "not_requested";
    @Column(length = 1000)
    public String llmError;
    public Instant createdAt = Instant.now();
    public Instant startedAt;
    public Instant finishedAt;

    @Column(length = 1000)
    public String errorMessage;

    @Column(length = 1000)
    public String commentError;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    public String manualDiff;

    public boolean postComment;
}
