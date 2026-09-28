package com.hieuthuoc.web;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.AddressRepository;
import com.hieuthuoc.repository.ProductRepository;
import com.hieuthuoc.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CartController {
    private final Cart cart;
    private final CartService cartService;
    private final OrderService orderService;
    private final ProductRepository productRepo;
    private final AddressRepository addressRepo;
    private final CurrentUser currentUser;

    @GetMapping("/cart")
    public String view(Model model) {
        model.addAttribute("cart", cartService.build(cart, ShippingMethod.DELIVERY));
        model.addAttribute("title", "Giỏ hàng");
        return "shop/cart";
    }

    @PostMapping("/cart/add")
    public Object add(@RequestParam Long productId, @RequestParam(defaultValue = "1") int qty,
                      @RequestParam(required = false) String buyNow,
                      @RequestHeader(value = "Accept", required = false) String accept,
                      @RequestHeader(value = "Referer", required = false) String referer,
                      RedirectAttributes ra) {
        boolean json = accept != null && accept.contains("application/json");
        User u = currentUser.getOrNull();
        String error;
        String message = null;
        Product p = productRepo.findById(productId).orElse(null);
        if (u != null && u.isStaff()) error = "Tài khoản nhân viên không thể đặt hàng online.";
        else {
            qty = Math.max(1, Math.min(qty, 999));
            error = cartService.checkAdd(p, cart.quantityOf(productId) + qty);
            if (error == null) {
                cart.getItems().merge(productId, qty, Integer::sum);
                message = "Đã thêm " + qty + " " + p.getUnit() + " \"" + p.getName() + "\" vào giỏ hàng."
                        + (p.getDrugType().isPrescription() ? " Đây là thuốc kê đơn - bạn cần tải lên đơn thuốc khi đặt hàng." : "");
            }
        }
        if (json) {
            return ResponseEntity.status(error == null ? 200 : 400)
                    .body(Map.of("ok", error == null, "message", error == null ? message : error, "count", cart.getCount()));
        }
        if (error != null) Flash.error(ra, error);
        else Flash.success(ra, message);
        if (error == null && buyNow != null) return "redirect:/cart";
        return "redirect:" + (p != null ? "/products/" + p.getSlug() : "/cart");
    }

    @PostMapping("/cart/update")
    public String update(@RequestParam Map<String, String> params, RedirectAttributes ra) {
        params.forEach((k, v) -> {
            if (!k.startsWith("qty_")) return;
            try {
                Long id = Long.valueOf(k.substring(4));
                int q = Integer.parseInt(v.trim());
                if (!cart.getItems().containsKey(id)) return;
                if (q <= 0) cart.getItems().remove(id);
                else cart.getItems().put(id, Math.min(q, 999));
            } catch (NumberFormatException ignored) {
            }
        });
        Flash.info(ra, "Đã cập nhật giỏ hàng.");
        return "redirect:/cart";
    }

    @PostMapping("/cart/remove/{id}")
    public String remove(@PathVariable Long id) {
        cart.getItems().remove(id);
        return "redirect:/cart";
    }

    @PostMapping("/cart/voucher")
    public String voucher(@RequestParam(required = false) String code, @RequestParam(required = false) String remove,
                          @RequestParam(defaultValue = "/cart") String back, RedirectAttributes ra) {
        String target = back.startsWith("/checkout") ? "/checkout" : "/cart";
        if (remove != null || Texts.isBlank(code)) {
            cart.setVoucherCode(null);
            return "redirect:" + target;
        }
        String c = code.trim().toUpperCase();
        String prev = cart.getVoucherCode();
        cart.setVoucherCode(c);
        CartService.View v = cartService.build(cart, ShippingMethod.DELIVERY);
        if (v.getVoucherError() != null) {
            cart.setVoucherCode(prev);
            Flash.error(ra, v.getVoucherError());
        } else {
            Flash.success(ra, "Áp dụng mã " + c + " thành công.");
        }
        return "redirect:" + target;
    }

    /* ---------------- Thanh toán ---------------- */

    private void checkoutModel(Model model, User u, OrderService.CheckoutForm form) {
        model.addAttribute("cart", cartService.build(cart, form.getShippingMethod() == null ? ShippingMethod.DELIVERY : form.getShippingMethod()));
        model.addAttribute("addresses", addressRepo.findByUserOrderByDefaultAddressDescIdAsc(u));
        model.addAttribute("form", form);
        model.addAttribute("shippingMethods", ShippingMethod.values());
        model.addAttribute("paymentMethods", PaymentMethod.values());
        model.addAttribute("title", "Thanh toán");
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam(required = false) ShippingMethod shipping, Model model, RedirectAttributes ra) {
        if (cart.getItems().isEmpty()) {
            Flash.warning(ra, "Giỏ hàng đang trống.");
            return "redirect:/cart";
        }
        User u = currentUser.get();
        List<Address> addresses = addressRepo.findByUserOrderByDefaultAddressDescIdAsc(u);
        OrderService.CheckoutForm form = new OrderService.CheckoutForm();
        if (!addresses.isEmpty()) {
            form.setRecipient(addresses.get(0).getRecipient());
            form.setPhone(addresses.get(0).getPhone());
            form.setAddress(addresses.get(0).getAddressLine());
        } else {
            form.setRecipient(u.getFullName());
            form.setPhone(u.getPhone());
        }
        if (shipping != null) form.setShippingMethod(shipping);
        checkoutModel(model, u, form);
        return "shop/checkout";
    }

    @PostMapping("/checkout")
    public String placeOrder(@ModelAttribute("form") OrderService.CheckoutForm form,
                             @RequestParam(value = "prescription", required = false) MultipartFile prescription,
                             Model model, RedirectAttributes ra) {
        User u = currentUser.get();
        Order o;
        try {
            o = orderService.placeOrder(u, cart, form, prescription);
        } catch (OrderService.CheckoutException e) {
            checkoutModel(model, u, form);
            model.addAttribute("errors", e.getErrors());
            return "shop/checkout";
        }
        if (!o.isNeedsPrescription() && o.getPaymentMethod() == PaymentMethod.ONLINE) {
            return "redirect:/account/orders/" + o.getCode() + "/pay";
        }
        Flash.success(ra, o.isNeedsPrescription()
                ? "Đặt hàng thành công (mã " + o.getCode() + "). Dược sĩ sẽ kiểm tra đơn thuốc và phản hồi sớm nhất."
                : "Đặt hàng thành công! Mã đơn hàng: " + o.getCode() + ".");
        return "redirect:/account/orders/" + o.getCode();
    }
}
