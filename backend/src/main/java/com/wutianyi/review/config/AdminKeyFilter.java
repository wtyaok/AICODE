package com.wutianyi.review.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class AdminKeyFilter extends OncePerRequestFilter {
    private final ReviewConfig config;

    public AdminKeyFilter(ReviewConfig config) {
        this.config = config;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain chain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")
            || path.equals("/api/health")
            || path.startsWith("/api/webhooks/")) {
            chain.doFilter(request, response);
            return;
        }
        if (config.adminKey.isBlank()) {
            response.sendError(503, "ADMIN_API_KEY 未配置");
            return;
        }
        String provided = request.getHeader("X-Admin-Key");
        boolean valid = MessageDigest.isEqual(
            config.adminKey.getBytes(StandardCharsets.UTF_8),
            (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8)
        );
        if (!valid) {
            response.sendError(401, "Admin Key 无效");
            return;
        }
        chain.doFilter(request, response);
    }
}
