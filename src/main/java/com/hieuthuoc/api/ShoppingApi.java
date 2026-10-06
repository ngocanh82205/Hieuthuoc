package com.hieuthuoc.api;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * Mua hàng của khách: báo giá giỏ hàng, đặt hàng, đơn hàng của tôi, hủy / xác nhận / đổi trả, thanh toán online.
 * Giỏ hàng do client giữ (API phi trạng thái) và gửi lên dạng items[] mỗi lần báo giá / đặt hàng.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ShoppingApi {
    private final CartService carts;
    private final OrderService orders;
    private final PaymentService payments;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    /** Dựng giỏ hàng tạm từ items[i][productId|unitId|quantity] + voucher + usePoints. */
    private Cart cartOf(Form f) {
        Cart cart = new Cart();
        for (Map<String, String> row : f.rows("items")) {
            Long pid = Texts.toLong(row.get("productId"));
            int qty = Texts.toInt(row.get("quantity"), 0);
            if (pid == null || qty <= 0) continue;
            Long unitId = Texts.toLong(row.get("unitId"));
            cart.add(pid, unitId, qty);
        }
        String voucher = Texts.trim(f.get("voucher"));
        if (!voucher.isEmpty()) cart.setVoucher(voucher.toUpperCase());
        cart.setUsePoints(f.bool("usePoints"));
        if (cart.items().isEmpty()) throw new ValidationException(List.of("Giỏ hàng đang trống."));
        return cart;
    }

    /**
     * Báo giá giỏ hàng: tính giá theo đơn vị, khuyến mãi, voucher, điểm, phí giao hàng.
     * Body: {"items": [{"productId", "unitId", "quantity"}], "voucher", "usePoints", "shippingMethod": "DELIVERY|PICKUP", "province"}
     */
    @PostMapping("/cart/quote")
    @Transactional(readOnly = true)
    public Map<String, Object> quote(@RequestBody Map<String, Object> body) {
        Form f = Api.form(body);
        Cart cart = cartOf(f);
        ShippingMethod method = "PICKUP".equals(f.get("shippingMethod")) ? ShippingMethod.PICKUP : ShippingMethod.DELIVERY;
        cart.setProvince(Texts.emptyToNull(f.get("province")));
        return Api.ok(Dto.QuoteDto.of(carts.build(cart, method, currentUser.get())));
    }

    /** Chuyển tham số API (camelCase) sang tham số form của OrderService. */
    private Form orderForm(Form f) {
        org.springframework.util.LinkedMultiValueMap<String, String> m = new org.springframework.util.LinkedMultiValueMap<>();
        Map<String, String> names = Map.ofEntries(Map.entry("recipient", "recipient"), Map.entry("phone", "phone"), Map.entry("address", "address"),
                Map.entry("province", "province"), Map.entry("shippingMethod", "shipping_method"), Map.entry("paymentMethod", "payment_method"),
                Map.entry("note", "note"), Map.entry("ghnDistrictId", "ghn_district_id"), Map.entry("ghnWardCode", "ghn_ward_code"),
                Map.entry("requestVat", "request_vat"), Map.entry("vatCompany", "vat_company"), Map.entry("vatTaxCode", "vat_tax_code"),
                Map.entry("vatAddress", "vat_address"), Map.entry("vatEmail", "vat_email"));
        names.forEach((api, form) -> {
            String v = f.get(api);
            if (v != null) m.add(form, v);
        });
        return new Form(m);
    }

    private ResponseEntity<Map<String, Object>> place(Form f, MultipartFile prescription) {
        Order o = orders.placeOrder(currentUser.get(), cartOf(f), orderForm(f), prescription);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", Dto.OrderDto.of(o, true));
        data.put("nextAction", o.isNeedsPrescription() ? "WAIT_PRESCRIPTION_REVIEW" : o.getPaymentMethod().isGateway() ? "PAY_ONLINE" : "NONE");
        return Api.created(data, o.isNeedsPrescription()
                ? "Đặt hàng thành công (mã " + o.getCode() + "). Dược sĩ sẽ kiểm tra đơn thuốc và phản hồi sớm nhất."
                : "Đặt hàng thành công! Mã đơn hàng: " + o.getCode() + ".");
    }

    /**
     * Đặt hàng (JSON) - dùng khi giỏ không có thuốc kê đơn.
     * Body: {"items": [...], "voucher", "usePoints", "shippingMethod", "paymentMethod": "COD|BANK_TRANSFER|PAYOS|VNPAY",
     * "recipient", "phone", "province", "address", "ghnDistrictId", "ghnWardCode", "note", "requestVat", "vatCompany", ...}
     */
    @PostMapping(value = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> placeJson(@RequestBody Map<String, Object> body) {
        return place(Api.form(body), null);
    }

    /** Đặt hàng (multipart/form-data) - cùng các trường như bản JSON (items[0][productId]...) kèm tệp "prescription" khi có thuốc kê đơn. */
    @PostMapping(value = "/orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<Map<String, Object>> placeMultipart(@RequestParam MultiValueMap<String, String> params,
                                                              @RequestParam(required = false) MultipartFile prescription) {
        return place(new Form(params), prescription);
    }

    private Order myOrder(String code) {
        return em.createQuery("select o from Order o where o.code = :c and o.user.id = :u", Order.class)
                .setParameter("c", code).setParameter("u", currentUser.get().getId())
                .getResultStream().findFirst().orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn hàng."));
    }

    /** Đơn hàng của tôi. Tham số: status (mã trạng thái), page. */
    @GetMapping("/orders")
    @Transactional(readOnly = true)
    public Map<String, Object> myOrders(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page) {
        OrderStatus st = null;
        try {
            if (status != null && !status.isBlank()) st = OrderStatus.valueOf(status);
        } catch (IllegalArgumentException ignored) {
            // trạng thái không hợp lệ: bỏ lọc
        }
        Page<Order> p = new com.hieuthuoc.web.Jpql("Order o", "o").where("o.user.id = :u", "u", currentUser.get().getId())
                .when(st != null, "o.status = :s", "s", st).page(em, Order.class, "o.id desc", 10, page);
        return Api.page(p, o -> Dto.OrderDto.of(o, false));
    }

    @GetMapping("/orders/{code}")
    @Transactional(readOnly = true)
    public Map<String, Object> order(@PathVariable String code) {
        Order o = myOrder(code);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("order", Dto.OrderDto.of(o, true));
        data.put("canCancel", o.getStatus().isCustomerCancellable());
        data.put("canPay", payments.canPay(o));
        data.put("canReturn", orders.canRequestReturn(o));
        data.put("bankTransferQr", orders.showBankTransfer(o) ? payments.vietQrUrl(o) : null);
        return Api.ok(data);
    }

    /** Body: {"reason": "..."} */
    @PostMapping("/orders/{code}/cancel")
    @Transactional
    public Map<String, Object> cancel(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        Order o = myOrder(code);
        orders.cancelByCustomer(o, currentUser.get(), Api.form(body).get("reason"));
        return Api.ok(Dto.OrderDto.of(o, true), "Đã hủy đơn hàng " + o.getCode() + ".");
    }

    /** Khách xác nhận đơn do dược sĩ lên / điều chỉnh (trạng thái AWAITING_CUSTOMER). Body: thông tin giao hàng như khi đặt. */
    @PostMapping("/orders/{code}/confirm")
    @Transactional
    public Map<String, Object> confirm(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        Order o = myOrder(code);
        orders.confirmByCustomer(o, currentUser.get(), orderForm(Api.form(body)));
        return Api.ok(Dto.OrderDto.of(o, true), "Đã xác nhận đơn hàng.");
    }

    /** Body: {"reason": "..."} - yêu cầu đổi / trả hàng sau khi nhận. */
    @PostMapping("/orders/{code}/return")
    @Transactional
    public Map<String, Object> requestReturn(@PathVariable String code, @RequestBody(required = false) Map<String, Object> body) {
        Order o = myOrder(code);
        orders.requestReturn(o, currentUser.get(), Api.form(body).get("reason"));
        return Api.ok(Dto.OrderDto.of(o, true), "Đã gửi yêu cầu đổi/trả.");
    }

    /** Tạo phiên thanh toán online (PayOS / VNPay, hoặc cổng thử nghiệm khi chạy local): trả URL để client mở. */
    @PostMapping("/orders/{code}/payment")
    @Transactional
    public Map<String, Object> pay(@PathVariable String code, HttpServletRequest req) {
        Order o = myOrder(code);
        if (!payments.canPay(o)) throw new BusinessException("Đơn hàng này không thể thanh toán online lúc này.", 409);
        return Api.ok(Map.of("paymentUrl", payments.start(o, req.getRemoteAddr())), "Mở paymentUrl để thanh toán.");
    }
}
