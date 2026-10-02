package com.wutianyi.review.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.wutianyi.review.config.ReviewConfig;
import com.wutianyi.review.model.ReviewTask;
import com.wutianyi.review.service.ReviewService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
public class WebhookController {
    private final ReviewService service;
    private final ReviewConfig config;

    public WebhookController(ReviewService service, ReviewConfig config) {
        this.service = service;
        this.config = config;
    }

    @PostMapping("/gitlab")
    public ResponseEntity<?> gitlab(
        @RequestHeader(value = "X-Gitlab-Token", required = false) String token,
        @RequestBody JsonNode event
    ) {
        if (config.webhookSecret.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "未配置 Webhook Secret"));
        }
        byte[] expected = config.webhookSecret.getBytes(StandardCharsets.UTF_8);
        byte[] provided = (token == null ? "" : token).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, provided)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Webhook Token 无效"));
        }

        String kind = event.path("object_kind").asText();
        String action = event.path("object_attributes").path("action").asText("");
        String before = event.path("before").asText("");
        String after = event.path("after").asText("");
        boolean reviewableMr = "merge_request".equals(kind)
            && List.of("open", "update", "reopen").contains(action);
        boolean reviewablePush = "push".equals(kind)
            && !before.isBlank() && !after.isBlank()
            && !before.matches("0+") && !after.matches("0+")
            && !before.equals(after);
        if (!reviewableMr && !reviewablePush) {
            return ResponseEntity.accepted().body(Map.of(
                "accepted", false,
                "reason", "非目标 MR 或 Push 事件"
            ));
        }

        ReviewTask task = service.createWebhook(event);
        return ResponseEntity.accepted().body(Map.of(
            "accepted", true,
            "taskId", task.id,
            "status", task.status
        ));
    }
}
