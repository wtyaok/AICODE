package com.wutianyi.review.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "review_feedback")
public class Feedback {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public Long findingId;

    @Column(nullable = false)
    public String taskId;

    @Column(nullable = false)
    public String status;

    @Column(length = 1000)
    public String commentText;

    public Instant createdAt = Instant.now();
}
