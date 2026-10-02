package com.wutianyi.review.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import com.wutianyi.review.api.Contracts.DiffFile;
import com.wutianyi.review.config.ReviewConfig;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class GitlabClientTest {
    @Test
    void fetchComparisonUsesProjectPathAndCommitRange() throws Exception {
        HttpServer server = HttpServer.create(
            new InetSocketAddress("127.0.0.1", 0), 0
        );
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/api/v4/", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            token.set(exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN"));
            byte[] response = (
                "{\"diffs\":[{\"old_path\":\"src/A.java\","
                    + "\"new_path\":\"src/A.java\","
                    + "\"diff\":\"@@ -0,0 +1 @@\\n+return null;\\n\"}] }"
            ).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.start();

        try {
            ReviewConfig config = new ReviewConfig();
            config.gitlabUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            config.gitlabToken = "test-token";
            GitlabClient client = new GitlabClient(RestClient.create(), config);

            List<DiffFile> files = client.fetchComparison(
                "group/project", "before-sha", "after-sha"
            );

            assertEquals(
                "/api/v4/projects/group%2Fproject/repository/compare"
                    + "?from=before-sha&to=after-sha",
                requestPath.get()
            );
            assertEquals("test-token", token.get());
            assertEquals(1, files.size());
            assertEquals("src/A.java", files.get(0).path());
            assertEquals("@@ -0,0 +1 @@\n+return null;\n", files.get(0).diff());
        } finally {
            server.stop(0);
        }
    }
}
