package com.hieuthuoc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Gọi Claude (Anthropic Messages API). Không có key: isConfigured() = false, trợ lý dùng trả lời theo kịch bản. */
@Component
public class ClaudeClient {
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.ai.anthropic-key:}")
    private String apiKey;
    @Value("${app.ai.anthropic-model:claude-sonnet-5-5}")
    private String model;
    @Value("${app.ai.anthropic-base-url:https://api.anthropic.com}")
    private String baseUrl;

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /** turns: [role, text]; các lượt liền nhau cùng vai trò được gộp, bắt đầu bằng user. */
    public String complete(String system, List<String[]> turns, int maxTokens) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("Chưa cấu hình ANTHROPIC_API_KEY");
        List<Map<String, String>> messages = new ArrayList<>();
        for (String[] t : turns) {
            if (t[1] == null || t[1].trim().isEmpty() || (messages.isEmpty() && !"user".equals(t[0]))) continue;
            Map<String, String> last = messages.isEmpty() ? null : messages.get(messages.size() - 1);
            if (last != null && last.get("role").equals(t[0])) last.put("content", last.get("content") + "\n" + t[1]);
            else messages.add(new HashMap<>(Map.of("role", t[0], "content", t[1])));
        }
        if (messages.isEmpty()) throw new IllegalStateException("Không có nội dung để gửi");
        Map<String, Object> body = Map.of("model", model, "max_tokens", maxTokens, "system", system, "messages", messages);
        String res = RestClient.create().post().uri(baseUrl.replaceAll("/+$", "") + "/v1/messages").contentType(MediaType.APPLICATION_JSON)
                .header("x-api-key", apiKey.trim()).header("anthropic-version", "2023-06-01").body(json.writeValueAsString(body))
                .exchange((rq, rs) -> {
                    String b = new String(rs.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (!rs.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Claude API trả về " + rs.getStatusCode().value());
                    return b;
                });
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : json.readTree(res).path("content")) if ("text".equals(c.path("type").asText())) sb.append(c.path("text").asText());
        return sb.toString().trim();
    }
}
