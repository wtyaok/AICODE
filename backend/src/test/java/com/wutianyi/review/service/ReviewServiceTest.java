package com.wutianyi.review.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wutianyi.review.config.ReviewConfig;
import com.wutianyi.review.model.ReviewTask;
import com.wutianyi.review.repo.FeedbackRepository;
import com.wutianyi.review.repo.FindingRepository;
import com.wutianyi.review.repo.RuleRepository;
import com.wutianyi.review.repo.TaskRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

class ReviewServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final ReviewWorker worker = mock(ReviewWorker.class);
    private final ReviewService service = new ReviewService(
        tasks,
        mock(FindingRepository.class),
        mock(FeedbackRepository.class),
        mock(RuleRepository.class),
        mock(StringRedisTemplate.class),
        new ReviewConfig(),
        worker
    );

    @Test
    void pushCreatesTaskWithCommitRange() throws Exception {
        when(tasks.saveAndFlush(any(ReviewTask.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        ReviewTask task = service.createWebhook(pushEvent());

        assertEquals("push", task.triggerType);
        assertEquals("a".repeat(40), task.baseSha);
        assertEquals("b".repeat(40), task.commitSha);
        assertEquals("refs/heads/main", task.ref);
        assertFalse(task.postComment);
        verify(worker).process(task.id);
    }

    @Test
    void repeatedPushReturnsExistingTaskWithoutNewWrite() throws Exception {
        ReviewTask existing = new ReviewTask();
        when(tasks.findByIdempotencyKey(any())).thenReturn(Optional.of(existing));

        ReviewTask task = service.createWebhook(pushEvent());

        assertSame(existing, task);
        verify(tasks, never()).saveAndFlush(any());
        verify(worker, never()).process(any());
    }

    private JsonNode pushEvent() throws Exception {
        return new ObjectMapper().readTree("""
            {
              "object_kind": "push",
              "project": {"id": 123},
              "before": "%s",
              "after": "%s",
              "ref": "refs/heads/main"
            }
            """.formatted("a".repeat(40), "b".repeat(40)));
    }
}
