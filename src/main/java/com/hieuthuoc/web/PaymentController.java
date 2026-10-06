package com.hieuthuoc.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Thanh toán online: trang thanh toán, cổng thử nghiệm, PayOS (return / cancel / webhook), VNPay (return / IPN). */
@Controller
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService payments;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private Order myOrder(String code) {
        return em.createQuery("select o from Order o where o.code = :c and o.user.id = :u", Order.class)
                .setParameter("c", code).setParameter("u", currentUser.get().getId())
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
    }

    @GetMapping("/account/orders/{code}/pay")
    @Transactional(readOnly = true)
    public String payPage(@PathVariable String code, Model model) {
        Order o = myOrder(code);
        if (!payments.canPay(o)) return "redirect:/account/orders/" + o.getCode();
        model.addAttribute("title", "Thanh toán đơn " + o.getCode());
        model.addAttribute("order", o);
        model.addAttribute("gatewayReady", o.getPaymentMethod() == PaymentMethod.PAYOS ? payments.payosConfigured() : payments.vnpayConfigured());
        model.addAttribute("local", payments.isLocal());
        return "shop/pay";
    }

    @PostMapping("/account/orders/{code}/pay")
    @Transactional
    public String pay(@PathVariable String code, HttpServletRequest req) {
        Order o = myOrder(code);
        return "redirect:" + payments.start(o, req.getRemoteAddr());
    }

    /** Cổng thanh toán giả lập (khi chưa cấu hình PayOS/VNPay - chỉ cho phép trong môi trường local/testing). */
    @GetMapping("/payment/sandbox/{code}")
    @Transactional(readOnly = true)
    public String sandbox(@PathVariable String code, Model model) {
        if (!payments.isLocal()) throw BusinessException.forbidden("Cổng thanh toán thử nghiệm chỉ khả dụng trong môi trường phát triển (local/testing).");
        Order o = myOrder(code);
        if (!payments.canPay(o)) return "redirect:/account/orders/" + o.getCode();
        model.addAttribute("title", "Cổng thanh toán thử nghiệm");
        model.addAttribute("order", o);
        return "shop/sandbox";
    }

    @PostMapping("/payment/sandbox/{code}")
    @Transactional
    public String sandboxComplete(@PathVariable String code, @RequestParam(required = false) String result, RedirectAttributes ra) {
        if (!payments.isLocal()) throw BusinessException.forbidden("Cổng thanh toán thử nghiệm chỉ khả dụng trong môi trường phát triển (local/testing).");
        Order o = myOrder(code);
        PaymentTransaction tx = payments.sandboxComplete(o, currentUser.get(), "success".equals(result));
        boolean ok = PaymentTransaction.SUCCESS.equals(tx.getStatus());
        ra.addFlashAttribute(ok ? "success" : "error", ok ? "Thanh toán thành công! Nhà thuốc sẽ xử lý đơn hàng của bạn." : "Thanh toán chưa thành công. Bạn có thể thử lại.");
        return "redirect:/account/orders/" + o.getCode();
    }

    /* -------- PayOS -------- */

    private PaymentTransaction latestPayos(Order o) {
        return em.createQuery("select t from PaymentTransaction t where t.order.id = :o and t.gateway = 'PAYOS' order by t.id desc", PaymentTransaction.class)
                .setParameter("o", o.getId()).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }

    private Order orderByCode(String code) {
        return em.createQuery("select o from Order o where o.code = :c", Order.class).setParameter("c", code).getResultStream().findFirst().orElse(null);
    }

    @GetMapping("/payment/payos/return")
    @Transactional
    public String payosReturn(@RequestParam(name = "order_id", required = false) String orderId, @RequestParam(required = false) String orderCode,
                              @RequestParam(required = false) String code, RedirectAttributes ra) {
        PaymentTransaction tx = null;
        Order order = null;
        if (orderCode != null && !orderCode.isEmpty()) {
            tx = payments.latestTx("PAYOS", orderCode);
            order = tx != null ? tx.getOrder() : null;
        }
        if (order == null && orderId != null && !orderId.isEmpty()) {
            order = orderByCode(orderId);
            if (order != null) tx = latestPayos(order);
        }
        if (order == null && code != null && code.length() > 4) {
            order = orderByCode(code);
            if (order != null) tx = latestPayos(order);
        }
        if (order == null) {
            Web.error(ra, "Không tìm thấy thông tin đơn hàng sau thanh toán PayOS.");
            return "redirect:/account/orders";
        }
        String back = "redirect:/account/orders/" + order.getCode();
        // BẢO MẬT: không đổi trạng thái giao dịch chỉ từ query string như status hay cancel.
        if (order.getPaymentStatus() == PaymentStatus.PAID || (tx != null && PaymentTransaction.SUCCESS.equals(tx.getStatus()))) {
            Web.success(ra, "Thanh toán PayOS thành công! Nhà thuốc đã tiếp nhận đơn.");
            return back;
        }
        // Chỉ đối soát trực tiếp Server-to-Server với máy chủ PayOS khi giao dịch còn PENDING
        if (tx != null && PaymentTransaction.PENDING.equals(tx.getStatus())) {
            JsonNode d = payments.payosQueryPayment(tx.getGatewayOrderId());
            if (d != null) {
                String st = d.path("status").asText("");
                long paid = d.has("amountPaid") ? d.path("amountPaid").asLong() : d.path("amount").asLong();
                if ("PAID".equals(st) && paid >= order.getTotal()) {
                    String ref = d.path("reference").asText(d.path("paymentLinkId").asText(""));
                    payments.complete(tx, true, ref, "00", "Thanh toán PayOS thành công (đối soát trực tiếp máy chủ PayOS)", d.toString());
                    Web.success(ra, "Thanh toán PayOS thành công! Nhà thuốc đã tiếp nhận đơn.");
                    return back;
                }
                if ("CANCELLED".equals(st)) {
                    payments.complete(tx, false, "", "CANCELLED", "PayOS xác nhận giao dịch đã bị hủy", d.toString());
                    Web.error(ra, "Giao dịch thanh toán PayOS đã bị hủy.");
                    return back;
                }
            }
        }
        if (tx != null && PaymentTransaction.FAILED.equals(tx.getStatus())) {
            Web.error(ra, "Giao dịch thanh toán PayOS chưa thành công hoặc đã bị hủy.");
            return back;
        }
        Web.info(ra, "Đang chờ hệ thống xác nhận thanh toán từ PayOS hoặc giao dịch chưa hoàn tất.");
        return back;
    }

    @GetMapping("/payment/payos/cancel")
    @Transactional
    public String payosCancel(@RequestParam(name = "order_id", required = false) String orderId, @RequestParam(required = false) String code,
                              @RequestParam(required = false) String orderCode, RedirectAttributes ra) {
        String oid = orderId != null && !orderId.isEmpty() ? orderId : code;
        Order order = null;
        PaymentTransaction tx = null;
        if (orderCode != null && !orderCode.isEmpty()) {
            tx = payments.latestTx("PAYOS", orderCode);
            order = tx != null ? tx.getOrder() : null;
        }
        if (order == null && oid != null && oid.length() > 4) {
            order = orderByCode(oid);
            if (order != null) tx = latestPayos(order);
        }
        if (order == null) {
            Web.info(ra, "Bạn đã quay lại từ cổng thanh toán.");
            return "redirect:/account/orders";
        }
        // BẢO MẬT: route cancel là public, chỉ cập nhật FAILED khi máy chủ PayOS xác nhận đã hủy.
        if (tx != null && PaymentTransaction.PENDING.equals(tx.getStatus())) {
            JsonNode d = payments.payosQueryPayment(tx.getGatewayOrderId());
            if (d != null && "CANCELLED".equals(d.path("status").asText(""))) {
                payments.complete(tx, false, "", "CANCELLED", "PayOS xác nhận giao dịch đã bị hủy", d.toString());
            }
        }
        Web.info(ra, "Bạn đã quay lại từ cổng thanh toán. Giao dịch vẫn có thể thanh toán tiếp hoặc hủy trong tài khoản.");
        return "redirect:/account/orders/" + order.getCode();
    }

    /** PayOS Webhook gọi sang khi khách chuyển khoản thành công. */
    @PostMapping("/payment/payos/webhook")
    @ResponseBody
    @Transactional
    public Map<String, Object> payosWebhook(@RequestBody(required = false) String body) {
        PaymentTransaction tx = null;
        try {
            tx = payments.payosWebhook(new ObjectMapper().readTree(body == null || body.isBlank() ? "{}" : body));
        } catch (Exception ignored) {
            // chữ ký sai / dữ liệu lỗi -> success = false
        }
        return Map.of("success", tx != null);
    }

    /* -------- VNPay -------- */

    @GetMapping("/payment/vnpay/return")
    @Transactional
    public String vnpayReturn(@RequestParam Map<String, String> query, RedirectAttributes ra) {
        Object[] r = payments.vnpayHandle(query);
        PaymentTransaction tx = (PaymentTransaction) r[0];
        if (tx == null) {
            Web.error(ra, "Không xác minh được kết quả thanh toán VNPay.");
            return "redirect:/account/orders";
        }
        em.refresh(tx);
        boolean ok = PaymentTransaction.SUCCESS.equals(tx.getStatus());
        ra.addFlashAttribute(ok ? "success" : "error", ok ? "Thanh toán VNPay thành công!" : "Thanh toán VNPay chưa thành công. Bạn có thể thử lại.");
        return "redirect:/account/orders/" + tx.getOrder().getCode();
    }

    @GetMapping("/payment/vnpay/ipn")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, String>> vnpayIpn(@RequestParam Map<String, String> query) {
        String rsp = (String) payments.vnpayHandle(query)[1];
        Map<String, String> messages = Map.of("00", "Confirm Success", "01", "Order not found", "02", "Order already confirmed",
                "04", "Invalid amount", "97", "Invalid signature");
        return ResponseEntity.ok(Map.of("RspCode", rsp, "Message", messages.getOrDefault(rsp, "Unknown error")));
    }
}
