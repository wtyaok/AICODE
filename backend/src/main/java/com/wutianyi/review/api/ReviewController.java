package com.wutianyi.review.api;

import com.wutianyi.review.api.Contracts.*;
import com.wutianyi.review.model.*;
import com.wutianyi.review.service.ReviewService;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReviewController {
    private final ReviewService service;

    public ReviewController(ReviewService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "review-backend");
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/tasks")
    public Page<TaskSummary> tasks(
        @RequestParam(defaultValue = "") String q,
        @RequestParam(defaultValue = "") String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return service.list(q, status, page, size);
    }

    @GetMapping("/tasks/{id}")
    public TaskDetail task(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReviewTask review(
        @RequestBody ReviewRequest request,
        @RequestHeader(value = "Idempotency-Key", required = false) String key
    ) {
        return service.createManual(request, key);
    }

    @PostMapping("/findings/{id}/feedback")
    public Feedback feedback(
        @PathVariable long id,
        @RequestBody FeedbackRequest request
    ) {
        return service.addFeedback(id, request);
    }

    @GetMapping("/rules")
    public List<ReviewRule> rules() {
        return service.listRules();
    }

    @PutMapping("/rules/{id}")
    public ReviewRule putRule(
        @PathVariable String id,
        @RequestBody RuleRequest request
    ) {
        return service.saveRule(id, request);
    }
}
