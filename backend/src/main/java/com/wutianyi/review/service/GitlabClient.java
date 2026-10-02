package com.wutianyi.review.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.wutianyi.review.api.Contracts.DiffFile;
import com.wutianyi.review.config.ReviewConfig;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitlabClient {
    private final RestClient client;
    private final ReviewConfig config;

    public GitlabClient(RestClient client, ReviewConfig config) {
        this.client = client;
        this.config = config;
    }

    public record MergeRequest(String title, String sha, List<DiffFile> files) {}

    public List<DiffFile> fetchComparison(
        String projectId, String baseSha, String commitSha
    ) {
        String from = URLEncoder.encode(baseSha, StandardCharsets.UTF_8);
        String to = URLEncoder.encode(commitSha, StandardCharsets.UTF_8);
        JsonNode comparison = get(
            projectPath(projectId) + "/repository/compare?from=" + from + "&to=" + to
        );
        return parseDiffs(comparison.path("diffs"));
    }

    public MergeRequest fetchMergeRequest(String projectId, long iid) {
        JsonNode mr = get(path(projectId, iid, ""));
        JsonNode versions = get(path(projectId, iid, "/versions"));

        if (!versions.isArray() || versions.isEmpty()) {
            throw new IllegalStateException("GitLab 未返回 MR 版本");
        }

        long versionId = versions.get(0).path("id").asLong();
        JsonNode version = get(path(projectId, iid, "/versions/" + versionId));
        List<DiffFile> files = parseDiffs(version.path("diffs"));

        String sha = version.path("head_commit_sha").asText(
            mr.path("sha").asText("")
        );
        return new MergeRequest(mr.path("title").asText("MR !" + iid), sha, files);
    }

    public long postComment(String projectId, long iid, String body) {
        JsonNode response = client.post()
            .uri(uri(path(projectId, iid, "/notes")))
            .header("PRIVATE-TOKEN", token())
            .body(java.util.Map.of("body", body))
            .retrieve()
            .body(JsonNode.class);
        if (response == null || !response.has("id")) {
            throw new IllegalStateException("GitLab 未返回评论 ID");
        }
        return response.path("id").asLong();
    }

    private JsonNode get(String path) {
        JsonNode data = client.get()
            .uri(uri(path))
            .header("PRIVATE-TOKEN", token())
            .retrieve()
            .body(JsonNode.class);
        if (data == null) {
            throw new IllegalStateException("GitLab 返回空响应");
        }
        return data;
    }

    private String token() {
        if (config.gitlabToken.isBlank()) {
            throw new IllegalStateException("未配置 GITLAB_TOKEN");
        }
        return config.gitlabToken;
    }

    private String path(String projectId, long iid, String suffix) {
        return projectPath(projectId) + "/merge_requests/" + iid + suffix;
    }

    private String projectPath(String projectId) {
        return "/projects/" + URLEncoder.encode(projectId, StandardCharsets.UTF_8);
    }

    private List<DiffFile> parseDiffs(JsonNode diffs) {
        if (!diffs.isArray()) {
            throw new IllegalStateException("GitLab 未返回 diff 列表");
        }

        List<DiffFile> files = new ArrayList<>();
        for (JsonNode item : diffs) {
            if (item.path("too_large").asBoolean() || item.path("collapsed").asBoolean()) {
                throw new IllegalStateException("GitLab diff 被截断，无法完整评审");
            }
            String diff = item.path("diff").asText("");
            if (!diff.isBlank()) {
                files.add(new DiffFile(
                    item.path("new_path").asText(),
                    item.path("old_path").asText(),
                    diff
                ));
            }
        }
        return files;
    }

    private URI uri(String path) {
        return URI.create(config.gitlabUrl.replaceAll("/$", "") + "/api/v4" + path);
    }
}
