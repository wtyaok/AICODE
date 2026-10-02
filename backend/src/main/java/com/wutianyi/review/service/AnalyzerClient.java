package com.wutianyi.review.service;

import com.wutianyi.review.api.Contracts.AnalyzerRequest;
import com.wutianyi.review.api.Contracts.AnalyzerResponse;
import com.wutianyi.review.config.ReviewConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class AnalyzerClient {
    private final RestClient client;
    private final ReviewConfig config;

    public AnalyzerClient(RestClient client, ReviewConfig config) {
        this.client = client;
        this.config = config;
    }

    public AnalyzerResponse analyze(AnalyzerRequest request) {
        AnalyzerResponse response = client.post()
            .uri(config.analyzerUrl + "/analyze")
            .body(request)
            .retrieve()
            .body(AnalyzerResponse.class);
        if (response == null || response.findings() == null) {
            throw new IllegalStateException("Python 分析服务返回无效结果");
        }
        return response;
    }
}
