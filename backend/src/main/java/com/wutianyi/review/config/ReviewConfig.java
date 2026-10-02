package com.wutianyi.review.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

@Configuration
public class ReviewConfig {
    @Value("${review.gitlab-url}")
    public String gitlabUrl;

    @Value("${review.gitlab-token}")
    public String gitlabToken;

    @Value("${review.webhook-secret}")
    public String webhookSecret;

    @Value("${review.admin-key}")
    public String adminKey;

    @Value("${review.analyzer-url}")
    public String analyzerUrl;

    @Value("${review.max-diff-chars}")
    public int maxDiffChars;

    @Value("${review.comment-enabled}")
    public boolean commentEnabled;

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(60_000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public TaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-");
        executor.initialize();
        return executor;
    }
}
