package com.hieuthuoc.web;

import com.hieuthuoc.entity.Order;
import com.hieuthuoc.entity.OrderStatus;
import com.hieuthuoc.entity.Role;
import com.hieuthuoc.entity.ShippingMethod;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Địa giới hành chính & phí giao hàng GHN cho trang thanh toán; webhook cập nhật trạng thái vận đơn. */
@Slf4j
@RestController
@RequestMapping("/shipping")
@RequiredArgsConstructor
public class ShippingController {
    private final GhnService ghn;
    private final Cart cart;
    private final CartService carts;
    private final OrderService orders;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/provinces")
    public List<Map<String, Object>> provinces() {
        return ghn.provinces();
    }

    @GetMapping("/districts")
    public List<Map<String, Object>> districts(@RequestParam(name = "province_id", defaultValue = "0") int provinceId) {
        return ghn.districts(provinceId);
    }

    @GetMapping("/wards")
    public List<Map<String, Object>> wards(@RequestParam(name = "district_id", defaultValue = "0") int districtId) {
        return ghn.wards(districtId);
    }

    private static ShippingMethod method(String s) {
        try {
            return s == null ? ShippingMethod.DELIVERY : ShippingMethod.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ShippingMethod.DELIVERY;
        }
    }

    /** Phí giao hàng cho giỏ hiện tại (đã áp ngưỡng miễn phí). */
    @GetMapping("/fee")
    @Transactional(readOnly = true)
    public Map<String, Object> fee(@RequestParam(name = "shipping_method", required = false) String shippingMethod,
                                   @RequestParam(required = false) String province, @RequestParam(name = "district_id", required = false) String districtId,
                                   @RequestParam(name = "ward_code", required = false) String wardCode) {
        ShippingMethod m = method(shippingMethod);
        cart.setProvince(province == null || province.isEmpty() ? null : province);
        CartService.View cv = carts.build(cart, m, currentUser.getOrNull());
        Integer district = Texts.toInt(districtId, 0) > 0 ? Texts.toInt(districtId, 0) : null;
        long fee = orders.shippingFeeFor(m, cv.getAfterDiscount(), province, district, wardCode == null || wardCode.isEmpty() ? null : wardCode,
                cv.getWeight(), cv.getSubtotal());
        return Map.of("fee", fee, "total", cv.getAfterDiscount() + fee, "fee_text", fee == 0 ? "Miễn phí" : OrderService.money(fee),
                "total_text", OrderService.money(cv.getAfterDiscount() + fee));
    }

    /** Tự động nhận diện Tỉnh / Quận / Phường và tính phí GHN từ địa chỉ hoặc tọa độ. */
    @RequestMapping(value = "/resolve", method = {RequestMethod.GET, RequestMethod.POST})
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> resolve(@RequestParam(required = false) String query, @RequestParam(required = false) String lat,
                                                       @RequestParam(required = false) String lon, @RequestParam(name = "shipping_method", required = false) String shippingMethod) {
        Double la = parseCoord(lat, 90);
        Double lo = parseCoord(lon, 180);
        String q = query == null ? null : Texts.trim(query, 255);
        if ((q == null || q.isEmpty()) && (la == null || lo == null)) {
            return ResponseEntity.status(422).body(Map.of("ok", false, "message", "Vui lòng cung cấp địa chỉ hoặc tọa độ GPS."));
        }
        Map<String, Object> res = ghn.resolveAddress(q, la, lo);
        if (res == null || res.get("province") == null || res.get("district") == null || res.get("ward") == null) {
            return ResponseEntity.status(404).body(Map.of("ok", false, "message", "Không tìm thấy địa giới hành chính chính xác. Vui lòng tự chọn Tỉnh / Quận / Phường."));
        }
        ShippingMethod m = method(shippingMethod);
        @SuppressWarnings("unchecked") Map<String, Object> prov = (Map<String, Object>) res.get("province");
        @SuppressWarnings("unchecked") Map<String, Object> dist = (Map<String, Object>) res.get("district");
        @SuppressWarnings("unchecked") Map<String, Object> ward = (Map<String, Object>) res.get("ward");
        String province = String.valueOf(prov.get("name"));
        cart.setProvince(province);
        CartService.View cv = carts.build(cart, m, currentUser.getOrNull());
        long fee = orders.shippingFeeFor(m, cv.getAfterDiscount(), province, Texts.toInt(String.valueOf(dist.get("id")), 0),
                String.valueOf(ward.get("code")), cv.getWeight(), cv.getSubtotal());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        for (String k : List.of("province", "district", "ward", "districts", "wards", "lat", "lon", "road")) out.put(k, res.get(k));
        out.put("fee", fee);
        out.put("total", cv.getAfterDiscount() + fee);
        out.put("fee_text", fee == 0 ? "Miễn phí" : OrderService.money(fee));
        out.put("total_text", OrderService.money(cv.getAfterDiscount() + fee));
        return ResponseEntity.ok(out);
    }

    private static Double parseCoord(String s, double limit) {
        try {
            if (s == null || s.isBlank()) return null;
            double d = Double.parseDouble(s.trim());
            return Math.abs(d) <= limit ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Webhook GHN: cập nhật trạng thái vận đơn, tự hoàn tất đơn khi giao thành công. */
    @PostMapping("/ghn/webhook")
    @Transactional
    public ResponseEntity<Map<String, Object>> webhook(HttpServletRequest req, @RequestBody(required = false) String body) {
        String expected = ghn.webhookSecret();
        if (expected == null || expected.isEmpty()) {
            log.warn("GHN webhook bị từ chối: chưa cấu hình GHN_WEBHOOK_SECRET");
            return ResponseEntity.status(403).body(Map.of("ok", false, "message", "GHN webhook secret not configured"));
        }
        String incoming = firstNonEmpty(req.getHeader("X-GHN-Webhook-Secret"), req.getHeader("X-Webhook-Secret"), req.getHeader("X-Token"),
                bearer(req.getHeader("Authorization")), req.getParameter("token"), req.getParameter("secret"));
        String sig = firstNonEmpty(req.getHeader("X-GHN-Signature"), req.getHeader("X-Signature"));
        String content = body == null ? "" : body;
        boolean valid = false;
        if (sig != null) {
            valid = MessageDigest.isEqual(hmac(content, expected).getBytes(StandardCharsets.UTF_8), sig.getBytes(StandardCharsets.UTF_8));
        } else if (incoming != null) {
            valid = MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), incoming.getBytes(StandardCharsets.UTF_8));
        }
        if (!valid) {
            log.warn("GHN webhook bị từ chối: sai secret hoặc signature");
            return ResponseEntity.status(401).body(Map.of("ok", false, "message", "Unauthorized"));
        }
        String code;
        String status;
        try {
            JsonNode n = new ObjectMapper().readTree(content.isEmpty() ? "{}" : content);
            code = n.path("OrderCode").asText("");
            status = n.path("Status").asText("");
        } catch (Exception e) {
            code = Objects.toString(req.getParameter("OrderCode"), "");
            status = Objects.toString(req.getParameter("Status"), "");
        }
        if (code.isEmpty() || status.isEmpty()) {
            return ResponseEntity.status(400).body(Map.of("ok", false, "message", "OrderCode and Status are required"));
        }
        Order order = em.createQuery("select o from Order o where o.trackingCode = :c", Order.class).setParameter("c", code)
                .getResultStream().findFirst().orElse(null);
        if (order == null) return ResponseEntity.ok(Map.of("ok", true, "message", "Order not found"));
        // Idempotent: nếu trạng thái vận chuyển không thay đổi, không ghi trùng lịch sử
        if (status.equals(order.getShippingStatus())) return ResponseEntity.ok(Map.of("ok", true, "message", "Status unchanged"));
        order.setShippingStatus(status);
        orders.addHistory(order, order.getStatus(), "GHN: " + GhnService.statusLabel(status), null);
        if ((status.equals("delivered") || status.equals("returned")) && order.getStatus() == OrderStatus.SHIPPING) {
            User admin = em.createQuery("select u from User u where u.role = :r order by u.id", User.class).setParameter("r", Role.ADMIN)
                    .setMaxResults(1).getResultStream().findFirst().orElse(null);
            if (admin != null) {
                if (status.equals("delivered")) orders.changeStatus(order, OrderStatus.COMPLETED, admin, "GHN xác nhận giao hàng thành công", false);
                else orders.changeStatus(order, OrderStatus.RETURNED, admin, "GHN đã hoàn hàng về shop", false);
            }
        }
        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String bearer(String h) {
        return h != null && h.startsWith("Bearer ") ? h.substring(7) : null;
    }

    private static String firstNonEmpty(String... vs) {
        for (String v : vs) if (v != null && !v.isEmpty()) return v;
        return null;
    }

    private static String hmac(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }
}
