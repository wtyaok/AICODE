package com.wutianyi.review.repo;

import com.wutianyi.review.model.Finding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FindingRepository extends JpaRepository<Finding, Long> {
    List<Finding> findByTaskIdOrderByIdAsc(String taskId);
    long countByFeedbackStatus(String feedbackStatus);
    long countBySeverity(String severity);
}
