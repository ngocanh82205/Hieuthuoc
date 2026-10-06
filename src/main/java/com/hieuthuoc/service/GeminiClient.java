package com.hieuthuoc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Gọi Google Gemini API (v1beta). Key truyền qua header x-goog-api-key; không bật tools / function calling. */
@Component
public class GeminiClient {
    private final ObjectMapper json = new ObjectMapper();

    @Value("${app.ai.gemini-key:}")
    private String apiKey;
    @Value("${app.ai.gemini-model:gemini-3.5-flash}")
    private String model;
    @Value("${app.ai.gemini-base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    public boolean isConfigured() {
        return !apiKey.isBlank();
    }

    public String model() {
        return model;
    }

    /** turns: [role, text]; role 'user' hoặc 'model'/'assistant'. */
    public String complete(String system, List<String[]> turns, int maxTokens) throws Exception {
        if (!isConfigured()) throw new IllegalStateException("Chưa cấu hình GEMINI_API_KEY");
        List<Map<String, Object>> contents = new ArrayList<>();
        StringBuilder lastText = null;
        String lastRole = null;
        List<StringBuilder> texts = new ArrayList<>();
        List<String> roles = new ArrayList<>();
        for (String[] t : turns) {
            String text = t[1] == null ? "" : t[1].trim();
            if (text.isEmpty()) continue;
            String role = "assistant".equals(t[0]) || "model".equals(t[0]) ? "model" : "user";
            // Gemini yêu cầu lượt đầu tiên phải là user
            if (roles.isEmpty() && !"user".equals(role)) continue;
            if (role.equals(lastRole)) {
                lastText.append("\n").append(text);
            } else {
                lastText = new StringBuilder(text);
                lastRole = role;
                texts.add(lastText);
                roles.add(role);
            }
        }
        if (roles.isEmpty()) throw new IllegalStateException("Không có nội dung tin nhắn để gửi đến Gemini");
        for (int i = 0; i < roles.size(); i++) contents.add(Map.of("role", roles.get(i), "parts", List.of(Map.of("text", texts.get(i).toString()))));
        Map<String, Object> gen = new LinkedHashMap<>();
        gen.put("temperature", 0.3);
        gen.put("maxOutputTokens", Math.max(maxTokens, 1500));
        // Tắt thinking token ngầm của Gemini Flash để phản hồi nhanh
        if (model.contains("flash") || model.contains("2.") || model.contains("3.")) gen.put("thinkingConfig", Map.of("thinkingBudget", 0));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("contents", contents);
        payload.put("generationConfig", gen);
        if (!system.isBlank()) payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", system))));
        String url = baseUrl.replaceAll("/+$", "") + "/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8).replace("+", "%20") + ":generateContent";
        String res = RestClient.create().post().uri(url).contentType(MediaType.APPLICATION_JSON).header("x-goog-api-key", apiKey.trim())
                .body(json.writeValueAsString(payload)).exchange((rq, rs) -> {
                    if (!rs.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Gemini API request failed with status " + rs.getStatusCode().value());
                    return new String(rs.getBody().readAllBytes(), StandardCharsets.UTF_8);
                });
        StringBuilder sb = new StringBuilder();
        for (JsonNode p : json.readTree(res).path("candidates").path(0).path("content").path("parts")) sb.append(p.path("text").asText(""));
        if (sb.toString().trim().isEmpty()) throw new IllegalStateException("Gemini API trả về nội dung rỗng");
        return sb.toString().trim();
    }
}
