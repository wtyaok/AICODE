package com.wutianyi.review.repo;

import com.wutianyi.review.model.ReviewRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RuleRepository extends JpaRepository<ReviewRule, String> {
    List<ReviewRule> findByEnabledTrue();
}
