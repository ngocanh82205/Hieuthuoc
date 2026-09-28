package com.hieuthuoc.web;

import com.hieuthuoc.entity.Product;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.ProductRepository;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.CurrentUser;
import com.hieuthuoc.service.CustomerCareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URI;
import java.util.Map;

/** Yêu thích, báo khi có hàng, hỏi đáp sản phẩm, yêu cầu gọi lại. */
@Controller
@RequiredArgsConstructor
public class CareController {
    private final CustomerCareService care;
    private final ProductRepository productRepo;
    private final CurrentUser currentUser;

    private static String back(HttpServletRequest req, String fallback) {
        String ref = req.getHeader("Referer");
        if (ref == null) return fallback;
        try {
            URI u = URI.create(ref);
            if (u.getHost() == null || !u.getHost().equalsIgnoreCase(req.getServerName())) return fallback;
            return u.getRawPath() + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    @PostMapping("/wishlist/toggle/{productId}")
    @Transactional
    public Object toggleWishlist(@PathVariable Long productId, @RequestHeader(value = "Accept", required = false) String accept,
                                 HttpServletRequest req, RedirectAttributes ra) {
        boolean added = care.toggleWishlist(currentUser.get(), productId);
        String msg = added ? "Đã thêm vào danh sách yêu thích." : "Đã bỏ khỏi danh sách yêu thích.";
        if (accept != null && accept.contains("application/json")) return ResponseEntity.ok(Map.of("ok", true, "added", added, "message", msg));
        Flash.success(ra, msg);
        return "redirect:" + back(req, "/account/wishlist");
    }

    @PostMapping("/stock-alert/{productId}")
    @Transactional
    public String subscribe(@PathVariable Long productId, @RequestParam(defaultValue = "false") boolean cancel, HttpServletRequest req, RedirectAttributes ra) {
        User u = currentUser.get();
        if (cancel) {
            care.unsubscribeStock(u, productId);
            Flash.info(ra, "Đã hủy đăng ký nhận thông báo.");
        } else {
            care.subscribeStock(u, productId);
            Flash.success(ra, "Đã đăng ký. Chúng tôi sẽ thông báo ngay khi sản phẩm có hàng trở lại.");
        }
        return "redirect:" + back(req, "/account/wishlist");
    }

    @PostMapping("/products/{slug}/questions")
    @Transactional
    public String ask(@PathVariable String slug, @RequestParam String question, RedirectAttributes ra) {
        Product p = productRepo.findBySlugAndActiveTrue(slug).orElseThrow(() -> BusinessException.notFound("Sản phẩm không tồn tại."));
        care.ask(currentUser.get(), p.getId(), question);
        Flash.success(ra, "Đã gửi câu hỏi. Dược sĩ sẽ trả lời và thông báo cho bạn.");
        return "redirect:/products/" + slug + "#qa";
    }

    /** Khách (kể cả chưa đăng nhập) để lại SĐT và khung giờ để dược sĩ gọi lại. */
    @PostMapping("/callback")
    @Transactional
    public String callback(@RequestParam String name, @RequestParam String phone,
                           @RequestParam(required = false) String preferredTime, @RequestParam(required = false) String note,
                           @RequestParam(required = false) Long productId, HttpServletRequest req, RedirectAttributes ra) {
        care.requestCallback(currentUser.getOrNull(), name, phone, preferredTime, note, productId);
        Flash.success(ra, "Đã nhận yêu cầu. Dược sĩ sẽ gọi lại cho bạn theo khung giờ đã chọn.");
        return "redirect:" + back(req, "/consult");
    }
}
