package com.hieuthuoc.web;

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
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/** Giỏ hàng (lưu trong phiên) và thanh toán. */
@Controller
@RequiredArgsConstructor
public class CartController {
    private final Cart cart;
    private final CartService carts;
    private final SettingService settings;
    private final CurrentUser currentUser;
    private final GhnService ghn;
    private final SafetyService safety;
    private final OrderService orders;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/cart")
    @Transactional(readOnly = true)
    public String index(Model model) {
        model.addAttribute("title", "Giỏ hàng");
        model.addAttribute("cart", carts.build(cart, ShippingMethod.DELIVERY, currentUser.getOrNull()));
        return "shop/cart";
    }

    @PostMapping("/cart/add")
    @Transactional(readOnly = true)
    public Object add(@RequestParam(name = "product_id", required = false) Long productId, @RequestParam(name = "unit_id", defaultValue = "0") Long unitId,
                      @RequestParam(defaultValue = "1") int qty, @RequestParam(required = false) String buyNow, HttpServletRequest req, RedirectAttributes ra) {
        User u = currentUser.getOrNull();
        Product p = productId == null ? null : em.find(Product.class, productId);
        int q = Math.max(1, Math.min(qty, 999));
        String error;
        String message = null;
        if (u != null && u.isStaff()) {
            error = "Tài khoản nhân viên không thể đặt hàng online.";
        } else {
            UnitOption unit = p == null ? null : p.findUnit(unitId);
            error = carts.checkAdd(cart, p, unit, q);
            if (error == null) {
                cart.add(p.getId(), unit.id(), q);
                message = "Đã thêm " + q + " " + unit.name() + " \"" + p.getName() + "\" vào giỏ hàng."
                        + (p.getDrugType().isPrescription() ? " Đây là thuốc kê đơn - bạn cần tải lên đơn thuốc khi đặt hàng." : "");
            }
        }
        if (Web.wantsJson(req)) {
            return ResponseEntity.status(error == null ? 200 : 400)
                    .body(Map.of("ok", error == null, "message", error != null ? error : message, "count", cart.count()));
        }
        if (error == null && buyNow != null && !buyNow.isEmpty()) {
            Web.success(ra, message);
            return "redirect:/cart";
        }
        ra.addFlashAttribute(error != null ? "error" : "success", error != null ? error : message);
        return "redirect:" + (p != null ? "/products/" + p.getSlug() : "/cart");
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam MultiValueMap<String, String> params, RedirectAttributes ra) {
        Map<String, Integer> items = cart.items();
        new Form(params).map("qty").forEach((key, q) -> {
            if (items.containsKey(key) && q != null && q.trim().matches("-?\\d+")) cart.set(key, Math.min(Integer.parseInt(q.trim()), 999));
        });
        Web.info(ra, "Đã cập nhật giỏ hàng.");
        return "redirect:/cart";
    }

    /** Tích chọn sản phẩm sẽ đặt hàng. */
    @PostMapping("/cart/select")
    public String select(@RequestParam MultiValueMap<String, String> params) {
        cart.select(new Form(params).list("keys"));
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove")
    public String remove(@RequestParam(required = false) String key) {
        if (key != null) cart.remove(key);
        return "redirect:/cart";
    }

    @PostMapping("/cart/points")
    public String points(@RequestParam(required = false) String use, @RequestParam(required = false) String back) {
        cart.setUsePoints("1".equals(use) || "true".equals(use) || "on".equals(use));
        return back != null && back.startsWith("checkout") ? "redirect:/checkout" : "redirect:/cart";
    }

    @PostMapping("/cart/voucher")
    @Transactional(readOnly = true)
    public String voucher(@RequestParam(required = false) String code, @RequestParam(required = false) String remove,
                          @RequestParam(required = false) String back, RedirectAttributes ra) {
        String target = back != null && back.startsWith("checkout") ? "redirect:/checkout" : "redirect:/cart";
        String c = Texts.trim(code);
        if ((remove != null && !remove.isEmpty()) || c.isEmpty()) {
            cart.setVoucher(null);
            return target;
        }
        String prev = cart.voucherCode();
        cart.setVoucher(c);
        CartService.View v = carts.build(cart, ShippingMethod.DELIVERY, currentUser.getOrNull());
        if (v.getVoucherError() != null) {
            cart.setVoucher(prev);
            Web.error(ra, v.getVoucherError());
            return target;
        }
        Web.success(ra, "Áp dụng mã " + c.toUpperCase() + " thành công.");
        return target;
    }

    /* ---------------- Thanh toán ---------------- */

    @GetMapping("/checkout")
    @Transactional(readOnly = true)
    public String checkout(@RequestParam(required = false) String shipping, Model model, RedirectAttributes ra) {
        if (cart.items().isEmpty()) {
            Web.warning(ra, "Giỏ hàng đang trống.");
            return "redirect:/cart";
        }
        if (cart.selectedItems().isEmpty()) {
            Web.warning(ra, "Vui lòng tích chọn ít nhất một sản phẩm để đặt hàng.");
            return "redirect:/cart";
        }
        User u = currentUser.get();
        List<Address> addresses = em.createQuery("select a from Address a where a.user.id = :u order by a.isDefault desc, a.id", Address.class)
                .setParameter("u", u.getId()).getResultList();
        Address first = addresses.isEmpty() ? null : addresses.get(0);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("recipient", first != null ? first.getRecipient() : u.getFullName());
        defaults.put("phone", first != null ? first.getPhone() : u.getPhone());
        defaults.put("address", first != null ? first.getAddressLine() : null);
        defaults.put("province", first != null ? first.getProvince() : null);
        defaults.put("ghn_province_id", first != null ? first.getGhnProvinceId() : null);
        defaults.put("ghn_district_id", first != null ? first.getGhnDistrictId() : null);
        defaults.put("ghn_ward_code", first != null ? first.getGhnWardCode() : null);
        defaults.put("shipping_method", shipping != null ? shipping : "DELIVERY");
        defaults.put("payment_method", settings.enabledPaymentMethods().get(0).name());
        // Ưu tiên dữ liệu khách vừa nhập (khi quay lại vì lỗi)
        @SuppressWarnings("unchecked")
        Map<String, String> old = model.getAttribute("old") instanceof Map<?, ?> m ? (Map<String, String>) m : Map.of();
        old.forEach((k, v) -> {
            if (v != null) defaults.put(k, v);
        });
        String province = (String) defaults.get("province");
        cart.setProvince(province == null || province.isEmpty() ? null : province);
        CartService.View cv = carts.build(cart, ShippingMethod.DELIVERY, u);
        model.addAttribute("title", "Thanh toán");
        model.addAttribute("cart", cv);
        // Cảnh báo dị ứng / chống chỉ định / tương tác theo hồ sơ sức khỏe khách tự khai báo
        model.addAttribute("safetyWarnings", safety.check(u, cv.getLines().stream().map(CartService.Line::getProduct).toList(), safety.recentProducts(u)));
        model.addAttribute("addresses", addresses);
        model.addAttribute("form", defaults);
        model.addAttribute("paymentMethods", settings.enabledPaymentMethods());
        model.addAttribute("shippingMethods", ShippingMethod.values());
        model.addAttribute("provinceFees", settings.provinceFees());
        model.addAttribute("ghnEnabled", ghn.enabled());
        return "shop/checkout";
    }

    @PostMapping("/checkout")
    @Transactional
    public String placeOrder(@RequestParam MultiValueMap<String, String> params, @RequestParam(required = false) MultipartFile prescription,
                             RedirectAttributes ra) {
        Map<String, List<String>> data = new LinkedHashMap<>(params);
        data.remove("_token");
        Order order = orders.placeOrder(currentUser.get(), cart, new Form(new org.springframework.util.LinkedMultiValueMap<>(data)), prescription);
        if (!order.isNeedsPrescription() && order.getPaymentMethod().isGateway()) {
            return "redirect:/account/orders/" + order.getCode() + "/pay";
        }
        String payNote = !order.isNeedsPrescription() && order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER ? " Vui lòng chuyển khoản theo hướng dẫn bên dưới." : "";
        Web.success(ra, order.isNeedsPrescription()
                ? "Đặt hàng thành công (mã " + order.getCode() + "). Dược sĩ sẽ kiểm tra đơn thuốc và phản hồi sớm nhất."
                : "Đặt hàng thành công! Mã đơn hàng: " + order.getCode() + "." + payNote);
        return "redirect:/account/orders/" + order.getCode();
    }
}
