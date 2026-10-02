package com.wutianyi.review.model;

import jakarta.persistence.*;

@Entity
@Table(name = "review_rules")
public class ReviewRule {
    @Id
    public String id;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public String severity;

    @Column(nullable = false, length = 1000)
    public String pattern;

    @Column(nullable = false, length = 1000)
    public String suggestion;

    public boolean enabled = true;
}
