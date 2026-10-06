package com.hieuthuoc.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieuthuoc.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Access token cho REST API: JWT ký HMAC-SHA256 (không lưu CSDL - API phi trạng thái).
 * Payload: sub (id người dùng), role, iat, exp. Khóa ký lấy từ app.api.secret (biến môi trường API_SECRET).
 */
@Service
public class TokenService {
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder D64 = Base64.getUrlDecoder();
    private final ObjectMapper json = new ObjectMapper();
    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(@Value("${app.api.secret:hieuthuoc-api-dev-secret-change-me}") String secret,
                        @Value("${app.api.token-ttl-hours:24}") long ttlHours) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlHours * 3600;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    public String issue(User u) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(u.getId()));
        payload.put("role", u.getRole().name());
        payload.put("iat", now);
        payload.put("exp", now + ttlSeconds);
        try {
            String head = B64.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String body = B64.encodeToString(json.writeValueAsBytes(payload));
            return head + "." + body + "." + sign(head + "." + body);
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được token", e);
        }
    }

    /** @return id người dùng nếu token hợp lệ và còn hạn, ngược lại null */
    public Long verify(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        try {
            byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII);
            if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.US_ASCII))) return null;
            Map<?, ?> payload = json.readValue(D64.decode(parts[1]), Map.class);
            long exp = ((Number) payload.get("exp")).longValue();
            if (Instant.now().getEpochSecond() >= exp) return null;
            return Long.parseLong(String.valueOf(payload.get("sub")));
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return B64.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }
}
