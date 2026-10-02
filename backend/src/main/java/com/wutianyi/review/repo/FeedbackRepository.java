package com.wutianyi.review.repo;

import com.wutianyi.review.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findByTaskIdOrderByCreatedAtDesc(String taskId);
}
