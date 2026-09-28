package com.hieuthuoc.config;

import com.hieuthuoc.entity.Category;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.CategoryRepository;
import com.hieuthuoc.repository.NotificationRepository;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.Cart;
import com.hieuthuoc.service.CurrentUser;
import com.hieuthuoc.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Dữ liệu dùng chung cho mọi trang + xử lý lỗi nghiệp vụ. */
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {
    private final CurrentUser currentUser;
    private final Cart cart;
    private final SettingService settings;
    private final CategoryRepository categoryRepo;
    private final NotificationRepository notificationRepo;
    private final com.hieuthuoc.service.CustomerCareService care;

    @ModelAttribute("currentUser")
    public User currentUser() {
        return currentUser.getOrNull();
    }

    @ModelAttribute("cartCount")
    public int cartCount() {
        return cart.getCount();
    }

    @ModelAttribute("settings")
    public Map<String, String> settings() {
        return settings.all();
    }

    @ModelAttribute("navCategories")
    public List<Category> navCategories() {
        return categoryRepo.findAllByOrderBySortOrderAscNameAsc();
    }

    /** Lưu ý: không nhận User qua @ModelAttribute (sẽ bị data-binding từ request vào entity). */
    @ModelAttribute("unreadCount")
    public long unreadCount(Model model) {
        return model.getAttribute("currentUser") instanceof User user ? notificationRepo.countByUserAndSeenFalse(user) : 0;
    }

    /** Id sản phẩm trong danh sách yêu thích của khách (để tô trái tim trên thẻ sản phẩm). */
    @ModelAttribute("wishlistIds")
    public java.util.Set<Long> wishlistIds(Model model) {
        return model.getAttribute("currentUser") instanceof User user && user.getRole() == com.hieuthuoc.entity.Role.CUSTOMER
                ? care.wishlistIds(user) : java.util.Set.of();
    }

    @ModelAttribute("requestPath")
    public String requestPath(HttpServletRequest req) {
        return req.getRequestURI();
    }

    /** Query string hiện tại (bỏ tham số page) - dùng để dựng link phân trang. */
    @ModelAttribute("pageQuery")
    public String pageQuery(HttpServletRequest req) {
        StringBuilder sb = new StringBuilder();
        req.getParameterMap().forEach((k, vs) -> {
            if (k.equals("page") || k.equals("_csrf")) return;
            for (String v : vs) {
                sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8)).append('=')
                        .append(URLEncoder.encode(v, StandardCharsets.UTF_8)).append('&');
            }
        });
        return sb.toString();
    }

    @ExceptionHandler(BusinessException.class)
    public Object business(BusinessException e, HttpServletRequest req, HttpServletResponse res) {
        return respond(e.getMessage(), e.getStatus(), req, res);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object tooLarge(HttpServletRequest req, HttpServletResponse res) {
        return respond("Ảnh vượt quá dung lượng cho phép (5MB).", 400, req, res);
    }

    private Object respond(String message, int status, HttpServletRequest req, HttpServletResponse res) {
        String accept = req.getHeader("Accept");
        if (accept != null && accept.contains("application/json")) {
            return ResponseEntity.status(status).body(Map.of("ok", false, "message", message));
        }
        String back = sameHostReferer(req);
        if ("POST".equals(req.getMethod()) && back != null) {
            RequestContextUtils.getOutputFlashMap(req).putAll(Map.of("flashType", "danger", "flashMsg", message));
            return "redirect:" + back;
        }
        ModelAndView mv = new ModelAndView("error");
        mv.addObject("status", status);
        mv.addObject("message", message);
        mv.setStatus(org.springframework.http.HttpStatus.valueOf(status));
        return mv;
    }

    /** Chỉ quay lại trang trước nếu cùng host (tránh open redirect). */
    private static String sameHostReferer(HttpServletRequest req) {
        String ref = req.getHeader("Referer");
        if (ref == null) return null;
        try {
            URI u = URI.create(ref);
            if (u.getHost() == null || !u.getHost().equalsIgnoreCase(req.getServerName())) return null;
            return u.getRawPath() + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
