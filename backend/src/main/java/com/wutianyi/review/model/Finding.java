package com.wutianyi.review.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_findings")
public class Finding {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String taskId;

    @Column(nullable = false)
    public String filePath;

    public Integer lineNumber;
    public String ruleId;
    public String category;
    public String severity;
    public String source;
    public String feedbackStatus = "open";
    public Instant createdAt = Instant.now();

    @Column(length = 2000)
    public String message;

    @Column(length = 2000)
    public String suggestion;
}
