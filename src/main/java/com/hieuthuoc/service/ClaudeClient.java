package com.hieuthuoc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gọi Claude (Anthropic Messages API). Cấu hình bằng biến môi trường ANTHROPIC_API_KEY (hoặc app.ai.api-key).
 * Không có key: isConfigured() = false, trợ lý dùng chế độ trả lời tự động theo kịch bản.
 */
@Slf4j
@Component
public class ClaudeClient {
    public record Turn(String role, String text) {
    }

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public ClaudeClient(@Value("${app.ai.api-key:}") String apiKey,
                        @Value("${app.ai.model:claude-sonnet-5}") String model,
                        @Value("${app.ai.base-url:https://api.anthropic.com}") String baseUrl) {
        String key = apiKey == null || apiKey.isBlank() ? System.getenv("ANTHROPIC_API_KEY") : apiKey;
        this.apiKey = key == null ? "" : key.trim();
        this.model = model;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public String model() {
        return model;
    }

    /**
     * Gửi hội thoại, trả về câu trả lời dạng văn bản. Các lượt liên tiếp cùng vai trò được gộp lại
     * (API yêu cầu user / assistant xen kẽ, bắt đầu bằng user).
     */
    public String complete(String system, List<Turn> turns, int maxTokens) {
        if (!isConfigured()) throw new IllegalStateException("Chưa cấu hình ANTHROPIC_API_KEY");
        List<Map<String, Object>> messages = new ArrayList<>();
        for (Turn t : turns) {
            if (t.text() == null || t.text().isBlank()) continue;
            if (messages.isEmpty() && !"user".equals(t.role())) continue;
            Map<String, Object> last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
            if (last != null && last.get("role").equals(t.role())) {
                last.put("content", last.get("content") + "\n" + t.text());
            } else {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("role", t.role());
                m.put("content", t.text());
                messages.add(m);
            }
        }
        if (messages.isEmpty()) throw new IllegalArgumentException("Không có nội dung để gửi");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("system", system);
        body.put("messages", messages);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/messages"))
                    .timeout(Duration.ofSeconds(45))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new IllegalStateException("Claude API trả về " + res.statusCode() + ": " + res.body().substring(0, Math.min(300, res.body().length())));
            }
            JsonNode root = json.readTree(res.body());
            StringBuilder sb = new StringBuilder();
            for (JsonNode c : root.path("content")) if ("text".equals(c.path("type").asText())) sb.append(c.path("text").asText());
            return sb.toString().trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Bị gián đoạn khi gọi Claude API", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Không kết nối được Claude API: " + e.getMessage(), e);
        }
    }
}
