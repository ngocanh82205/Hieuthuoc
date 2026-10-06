package com.hieuthuoc.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieuthuoc.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Cổng thanh toán: PayOS (VietQR ngân hàng), VNPay (thẻ ATM / Visa / QR), chuyển khoản VietQR.
 * Mỗi lần thanh toán tạo 1 bản ghi payment_transactions (đơn hàng - giao dịch: 1 - N).
 * Chưa cấu hình khóa cổng -> dùng trang thanh toán giả lập (sandbox) để demo trong local/testing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {
    private final OrderService orders;
    private final NotificationService notifications;
    private final MailService mail;
    private final SettingService settings;
    private final ObjectMapper json = new ObjectMapper();

    @PersistenceContext
    private EntityManager em;

    @Value("${app.env:local}")
    private String env;
    @Value("${app.url:http://localhost:8080}")
    private String appUrl;
    @Value("${app.payos.client-id:}")
    private String payosClientId;
    @Value("${app.payos.api-key:}")
    private String payosApiKey;
    @Value("${app.payos.checksum-key:}")
    private String payosChecksumKey;
    @Value("${app.payos.endpoint:https://api-merchant.payos.vn}")
    private String payosEndpoint;
    @Value("${app.vnpay.url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}")
    private String vnpayUrl;
    @Value("${app.vnpay.tmn-code:}")
    private String vnpayTmnCode;
    @Value("${app.vnpay.hash-secret:}")
    private String vnpayHashSecret;

    public boolean payosConfigured() {
        return !payosClientId.isBlank() && !payosApiKey.isBlank() && !payosChecksumKey.isBlank();
    }

    public boolean vnpayConfigured() {
        return !vnpayTmnCode.isBlank() && !vnpayHashSecret.isBlank();
    }

    public boolean isLocal() {
        return "local".equalsIgnoreCase(env) || "testing".equalsIgnoreCase(env);
    }

    private String url(String path) {
        return appUrl.replaceAll("/+$", "") + path;
    }

    public boolean canPay(Order o) {
        return o.getPaymentMethod().isGateway() && o.getPaymentStatus() == PaymentStatus.UNPAID
                && (o.getStatus() == OrderStatus.PENDING || o.getStatus() == OrderStatus.CONFIRMED);
    }

    /** Bắt đầu thanh toán: trả về URL chuyển hướng sang cổng. */
    public String start(Order o, String clientIp) {
        if (!canPay(o)) throw new BusinessException("Đơn hàng không ở trạng thái có thể thanh toán online.");
        long attempt = em.createQuery("select count(t) from PaymentTransaction t where t.order.id = :o", Long.class)
                .setParameter("o", o.getId()).getSingleResult() + 1;
        String gatewayOrderId = String.valueOf(o.getId() * 1000 + (attempt % 1000));
        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrder(o);
        tx.setGateway(o.getPaymentMethod().name());
        tx.setGatewayOrderId(gatewayOrderId);
        tx.setAmount(BigDecimal.valueOf(o.getTotal()));
        tx.setStatus(PaymentTransaction.PENDING);
        tx.setType("PAYMENT");
        em.persist(tx);
        return switch (o.getPaymentMethod()) {
            case PAYOS -> payosConfigured() ? payosCreate(o, tx) : sandboxUrlOrThrow(o);
            case VNPAY -> vnpayConfigured() ? vnpayUrl(o, tx, clientIp) : sandboxUrlOrThrow(o);
            default -> throw new BusinessException("Phương thức thanh toán không hỗ trợ thanh toán online.");
        };
    }

    private String sandboxUrlOrThrow(Order o) {
        if (!isLocal()) {
            throw new BusinessException("Cổng thanh toán " + o.getPaymentMethod().getLabel()
                    + " hiện chưa sẵn sàng. Quý khách vui lòng chọn hình thức Thanh toán khi nhận hàng (COD) hoặc Chuyển khoản ngân hàng.");
        }
        return "/payment/sandbox/" + o.getCode();
    }

    /* ------------------------------ PayOS ------------------------------ */

    private RestClient http(int timeoutSec) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutSec * 1000);
        f.setReadTimeout(timeoutSec * 1000);
        return RestClient.builder().requestFactory(f).build();
    }

    private String payosCreate(Order o, PaymentTransaction tx) {
        long orderCode = Long.parseLong(tx.getGatewayOrderId());
        long amount = o.getTotal();
        String description = ("Don " + o.getCode()).length() > 25 ? ("Don " + o.getCode()).substring(0, 25) : "Don " + o.getCode();
        String returnUrl = url("/payment/payos/return?order_id=" + o.getCode());
        String cancelUrl = url("/payment/payos/cancel?order_id=" + o.getCode());
        String signatureData = "amount=" + amount + "&cancelUrl=" + cancelUrl + "&description=" + description + "&orderCode=" + orderCode + "&returnUrl=" + returnUrl;
        String signature = hmac("HmacSHA256", payosChecksumKey, signatureData);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderCode", orderCode);
        payload.put("amount", amount);
        payload.put("description", description);
        payload.put("returnUrl", returnUrl);
        payload.put("cancelUrl", cancelUrl);
        payload.put("signature", signature);
        JsonNode res = null;
        try {
            String body = http(15).post().uri(payosEndpoint.replaceAll("/+$", "") + "/v2/payment-requests").contentType(MediaType.APPLICATION_JSON)
                    .header("x-client-id", payosClientId).header("x-api-key", payosApiKey).body(json.writeValueAsString(payload))
                    .exchange((rq, rs) -> new String(rs.getBody().readAllBytes(), StandardCharsets.UTF_8));
            res = json.readTree(body);
        } catch (Exception e) {
            log.error("PayOS create lỗi: {}", e.getMessage());
        }
        String checkout = res == null ? "" : res.path("data").path("checkoutUrl").asText("");
        if (checkout.isEmpty()) {
            tx.setStatus(PaymentTransaction.FAILED);
            tx.setResponseCode(res == null ? "" : res.path("code").asText(""));
            String desc = res == null || res.path("desc").isMissingNode() ? null : res.path("desc").asText();
            tx.setMessage(desc != null ? Texts.limit(desc, 300, "") : "Không kết nối được cổng thanh toán PayOS");
            tx.setPayload(res == null ? null : res.toString());
            throw new BusinessException("Không tạo được liên kết PayOS: " + (desc != null ? desc : "lỗi kết nối") + ". Vui lòng thử lại hoặc chọn cách khác.");
        }
        tx.setPayload(res.toString());
        return checkout;
    }

    /** Tra cứu thông tin thanh toán trực tiếp từ máy chủ PayOS (Server-to-Server). */
    public JsonNode payosQueryPayment(String orderCode) {
        if (payosClientId.isBlank() || payosApiKey.isBlank()) return null;
        try {
            String body = http(10).get().uri(payosEndpoint.replaceAll("/+$", "") + "/v2/payment-requests/" + orderCode)
                    .header("x-client-id", payosClientId).header("x-api-key", payosApiKey)
                    .exchange((rq, rs) -> new String(rs.getBody().readAllBytes(), StandardCharsets.UTF_8));
            JsonNode res = json.readTree(body);
            if ("00".equals(res.path("code").asText()) && res.has("data")) return res.get("data");
            log.warn("PayOS query payment không thành công: {} {}", orderCode, body);
        } catch (Exception e) {
            log.error("PayOS query payment lỗi: {}", e.getMessage());
        }
        return null;
    }

    public boolean payosVerify(JsonNode data, String signature) {
        if (signature == null || signature.isEmpty() || payosChecksumKey.isBlank() || data == null || !data.isObject()) return false;
        TreeMap<String, String> sorted = new TreeMap<>();
        data.fields().forEachRemaining(e -> {
            JsonNode v = e.getValue();
            if (v.isNull() || v.isArray() || v.isObject()) return;
            sorted.put(e.getKey(), v.isBoolean() ? (v.asBoolean() ? "1" : "") : v.asText());
        });
        StringJoiner parts = new StringJoiner("&");
        sorted.forEach((k, v) -> parts.add(k + "=" + v));
        return MessageDigest.isEqual(hmac("HmacSHA256", payosChecksumKey, parts.toString()).getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    /** Xử lý webhook PayOS gọi sang. */
    public PaymentTransaction payosWebhook(JsonNode payload) {
        JsonNode data = payload.path("data");
        String signature = payload.path("signature").asText("");
        if (!payosVerify(data, signature)) {
            log.warn("PayOS Webhook: sai chữ ký {}", payload);
            return null;
        }
        String orderCode = data.path("orderCode").asText("");
        PaymentTransaction tx = latestTx("PAYOS", orderCode);
        if (tx == null) {
            log.warn("PayOS Webhook: không tìm thấy giao dịch với orderCode={}", orderCode);
            return null;
        }
        // Đã ghi nhận thành công trước đó: bỏ qua (giao dịch FAILED vẫn đi tiếp để bắt trường hợp tiền về muộn)
        if (PaymentTransaction.SUCCESS.equals(tx.getStatus()) || PaymentTransaction.REFUNDED.equals(tx.getStatus())) return tx;
        boolean isSuccess = "00".equals(payload.path("code").asText()) && data.path("amount").asLong() == tx.amountValue();
        String ref = data.has("reference") ? data.path("reference").asText("") : data.path("paymentLinkId").asText("");
        return complete(tx, isSuccess, ref, payload.path("code").asText(""), payload.path("desc").isMissingNode() ? null : payload.path("desc").asText(),
                payload.toString());
    }

    public PaymentTransaction latestTx(String gateway, String gatewayOrderId) {
        List<PaymentTransaction> l = em.createQuery("select t from PaymentTransaction t where t.gateway = :g and t.gatewayOrderId = :id order by t.id desc",
                PaymentTransaction.class).setParameter("g", gateway).setParameter("id", gatewayOrderId).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    /* ------------------------------ VNPay ------------------------------ */

    private String vnpayUrl(Order o, PaymentTransaction tx, String ip) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        TreeMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnpayTmnCode);
        params.put("vnp_Amount", String.valueOf(o.getTotal() * 100));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", tx.getGatewayOrderId());
        params.put("vnp_OrderInfo", "Thanh toan don hang " + o.getCode());
        params.put("vnp_OrderType", "other");
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", url("/payment/vnpay/return"));
        params.put("vnp_IpAddr", ip == null || ip.isBlank() ? "127.0.0.1" : ip);
        params.put("vnp_CreateDate", now.format(f));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(f));
        String query = buildQuery(params);
        String hash = hmac("HmacSHA512", vnpayHashSecret, query);
        try {
            tx.setPayload(json.writeValueAsString(params));
        } catch (Exception ignored) {
        }
        return vnpayUrl + "?" + query + "&vnp_SecureHash=" + hash;
    }

    private static String buildQuery(Map<String, String> params) {
        StringJoiner sj = new StringJoiner("&");
        params.forEach((k, v) -> sj.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8)));
        return sj.toString();
    }

    public boolean vnpayVerify(Map<String, String> p) {
        String secure = p.getOrDefault("vnp_SecureHash", "");
        TreeMap<String, String> data = new TreeMap<>();
        p.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) data.put(k, v);
        });
        return !secure.isEmpty() && MessageDigest.isEqual(hmac("HmacSHA512", vnpayHashSecret, buildQuery(data)).getBytes(StandardCharsets.UTF_8),
                secure.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    /** @return [giao dịch, mã phản hồi IPN] */
    public Object[] vnpayHandle(Map<String, String> p) {
        if (!vnpayVerify(p)) return new Object[]{null, "97"};
        PaymentTransaction tx = latestTx("VNPAY", p.getOrDefault("vnp_TxnRef", ""));
        if (tx == null) return new Object[]{null, "01"};
        if (Optional.ofNullable(Texts.toLong(p.get("vnp_Amount"))).orElse(0L) != tx.amountValue() * 100) {
            return new Object[]{tx, "04"};
        }
        if (PaymentTransaction.SUCCESS.equals(tx.getStatus()) || PaymentTransaction.REFUNDED.equals(tx.getStatus())) return new Object[]{tx, "02"};
        boolean ok = "00".equals(p.getOrDefault("vnp_ResponseCode", "")) && "00".equals(p.getOrDefault("vnp_TransactionStatus", "00"));
        String code = p.getOrDefault("vnp_ResponseCode", "");
        String payload;
        try {
            payload = json.writeValueAsString(p);
        } catch (Exception e) {
            payload = null;
        }
        complete(tx, ok, p.getOrDefault("vnp_TransactionNo", ""), code,
                ok ? "Giao dịch thành công" : "Giao dịch không thành công (mã " + (code.isEmpty() ? "?" : code) + ")", payload);
        return new Object[]{tx, "00"};
    }

    /* ------------------------------ Sandbox (demo) ------------------------------ */

    public PaymentTransaction sandboxComplete(Order o, User user, boolean success) {
        if (!isLocal()) throw new BusinessException("Cổng thanh toán thử nghiệm chỉ khả dụng trong môi trường phát triển (local/testing).");
        List<PaymentTransaction> l = em.createQuery("select t from PaymentTransaction t where t.order.id = :o and t.status = :s and t.gateway = :g order by t.id desc",
                        PaymentTransaction.class).setParameter("o", o.getId()).setParameter("s", PaymentTransaction.PENDING)
                .setParameter("g", o.getPaymentMethod().name()).setMaxResults(1).getResultList();
        PaymentTransaction tx = l.isEmpty() ? null : l.get(0);
        if (tx == null) {
            if (!canPay(o)) throw new BusinessException("Đơn hàng không ở trạng thái có thể thanh toán.");
            tx = new PaymentTransaction();
            tx.setOrder(o);
            tx.setGateway(o.getPaymentMethod().name());
            tx.setGatewayOrderId(o.getCode() + "-S");
            tx.setAmount(BigDecimal.valueOf(o.getTotal()));
            tx.setStatus(PaymentTransaction.PENDING);
            em.persist(tx);
        }
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return complete(tx, success, "SANDBOX" + stamp, success ? "0" : "1006",
                success ? "Thanh toán thành công (cổng giả lập)" : "Khách hủy giao dịch (cổng giả lập)", "{\"sandbox\":true,\"by\":" + user.getId() + "}");
    }

    /** Cập nhật giao dịch + đơn hàng (idempotent: IPN và redirect có thể về cùng lúc). */
    public PaymentTransaction complete(PaymentTransaction tx0, boolean success, String transId, String code, String message, String payload) {
        PaymentTransaction tx = em.find(PaymentTransaction.class, tx0.getId(), LockModeType.PESSIMISTIC_WRITE);
        em.refresh(tx);
        if (PaymentTransaction.FAILED.equals(tx.getStatus()) && success) return latePayment(tx, transId, code, payload);
        if (!PaymentTransaction.PENDING.equals(tx.getStatus())) return tx;
        tx.setStatus(success ? PaymentTransaction.SUCCESS : PaymentTransaction.FAILED);
        tx.setTransactionId(transId == null || transId.isEmpty() ? null : transId);
        tx.setResponseCode(code);
        tx.setMessage(message != null ? Texts.limit(message, 300, "") : null);
        tx.setPayload(payload);
        tx.setPaidAt(success ? LocalDateTime.now().withNano(0) : null);
        Order o = em.find(Order.class, tx.getOrder().getId(), LockModeType.PESSIMISTIC_WRITE);
        em.refresh(o);
        if (success && o.getPaymentStatus() == PaymentStatus.UNPAID) {
            o.setPaymentStatus(PaymentStatus.PAID);
            orders.addHistory(o, o.getStatus(), "Khách thanh toán " + o.getPaymentMethod().getShortLabel() + " thành công"
                    + (transId != null && !transId.isEmpty() ? " (mã GD " + transId + ")" : ""), o.getUser());
            notifications.notifyPermission("ORDER", "Đơn " + o.getCode() + " đã thanh toán " + o.getPaymentMethod().getShortLabel(), "/staff/orders/" + o.getId());
            mail.send(o.getUser(), "Thanh toán thành công đơn " + o.getCode(),
                    List.of("Nhà thuốc đã nhận " + PromotionService.money(tx.amountValue()) + " qua " + o.getPaymentMethod().getLabel() + "."),
                    "Xem đơn hàng", mail.url("/account/orders/" + o.getCode()));
        } else if (success) {
            // Đơn đã hủy / đã thanh toán trước đó: ghi nhận để hoàn tiền
            notifications.notifyAdmins("Đơn " + o.getCode() + " nhận thanh toán " + o.getPaymentMethod().getShortLabel()
                    + " khi không còn chờ thanh toán - kiểm tra hoàn tiền.", "/admin/finance/transactions");
        }
        return tx;
    }

    /**
     * Cổng báo thành công cho giao dịch đã bị đánh dấu thất bại (VD: khách hủy đơn khi đang thanh toán):
     * ghi nhận tiền đã về; đơn đã hủy -> chuyển "chờ hoàn tiền" để admin hoàn lại cho khách.
     */
    private PaymentTransaction latePayment(PaymentTransaction tx, String transId, String code, String payload) {
        tx.setStatus(PaymentTransaction.SUCCESS);
        if (transId != null && !transId.isEmpty()) tx.setTransactionId(transId);
        tx.setResponseCode(code);
        tx.setMessage("Tiền về sau khi giao dịch đã bị hủy");
        tx.setPayload(payload);
        tx.setPaidAt(LocalDateTime.now().withNano(0));
        Order o = em.find(Order.class, tx.getOrder().getId(), LockModeType.PESSIMISTIC_WRITE);
        em.refresh(o);
        if (o.getPaymentStatus() == PaymentStatus.UNPAID) {
            if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.RX_REJECTED) {
                o.setPaymentStatus(PaymentStatus.REFUND_PENDING);
                o.setRefundAmount(tx.amountValue());
                orders.addHistory(o, o.getStatus(), "Nhận " + PromotionService.money(tx.amountValue()) + " qua " + o.getPaymentMethod().getShortLabel()
                        + " sau khi đơn đã hủy - chờ hoàn tiền", null);
            } else {
                o.setPaymentStatus(PaymentStatus.PAID);
                orders.addHistory(o, o.getStatus(), "Khách thanh toán " + o.getPaymentMethod().getShortLabel() + " thành công"
                        + (transId != null && !transId.isEmpty() ? " (mã GD " + transId + ")" : ""), o.getUser());
            }
        }
        notifications.notifyAdmins("Đơn " + o.getCode() + " nhận thanh toán " + PromotionService.money(tx.amountValue())
                + " sau khi giao dịch đã hủy - kiểm tra hoàn tiền.", "/admin/refunds");
        return tx;
    }

    /** Link ảnh VietQR cho chuyển khoản. */
    public String vietQrUrl(Order o) {
        return "https://img.vietqr.io/image/" + rawurlencode(settings.get("bank_code")) + "-" + rawurlencode(settings.get("bank_account"))
                + "-compact2.png?amount=" + o.getTotal() + "&addInfo=" + rawurlencode(o.getCode()) + "&accountName=" + rawurlencode(settings.get("bank_holder"));
    }

    private static String rawurlencode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    static String hmac(String algo, String key, String data) {
        try {
            Mac mac = Mac.getInstance(algo);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algo));
            byte[] d = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
