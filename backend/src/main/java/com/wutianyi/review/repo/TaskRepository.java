package com.wutianyi.review.repo;

import com.wutianyi.review.model.ReviewTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<ReviewTask, String> {
    Optional<ReviewTask> findByIdempotencyKey(String idempotencyKey);

    @Query("""
        select t from ReviewTask t
        where (:status = '' or t.status = :status)
          and (:query = '' or lower(t.title) like lower(concat('%', :query, '%'))
               or lower(t.projectId) like lower(concat('%', :query, '%')))
        """)
    Page<ReviewTask> search(String query, String status, Pageable pageable);
}
