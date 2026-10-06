package com.hieuthuoc.web;

import com.hieuthuoc.service.CurrentUser;
import com.hieuthuoc.service.CustomerCareService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Danh sách yêu thích. */
@Controller
@RequiredArgsConstructor
public class CareController {
    private final CustomerCareService care;
    private final CurrentUser currentUser;

    @PostMapping("/wishlist/toggle/{productId}")
    @Transactional
    public Object toggleWishlist(@PathVariable Long productId, HttpServletRequest req, RedirectAttributes ra) {
        boolean added = care.toggleWishlist(currentUser.get(), productId);
        String msg = added ? "Đã thêm vào danh sách yêu thích." : "Đã bỏ khỏi danh sách yêu thích.";
        if (Web.wantsJson(req)) return ResponseEntity.ok(Map.of("ok", true, "added", added, "message", msg));
        Web.success(ra, msg);
        return Web.back(req, "/account/wishlist");
    }
}
