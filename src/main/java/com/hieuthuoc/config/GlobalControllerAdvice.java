package com.hieuthuoc.config;

import com.hieuthuoc.entity.Category;
import com.hieuthuoc.entity.StaticPage;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.net.URI;
import java.util.*;

/**
 * Dữ liệu dùng chung cho mọi trang (như View::composer('*') của Laravel) và xử lý lỗi nghiệp vụ / lỗi nhập liệu
 * (như BusinessException::render và $request->validate()).
 */
@Slf4j
@ControllerAdvice(basePackages = "com.hieuthuoc.web")
@RequiredArgsConstructor
public class GlobalControllerAdvice {
    private static final Set<String> NO_OLD = Set.of("password", "password_confirmation", "current_password", "_csrf");

    private final CurrentUser currentUser;
    private final Cart cart;
    private final SettingService settings;
    private final CategoryService categories;

    @PersistenceContext
    private EntityManager em;

    @ModelAttribute("currentUser")
    public User currentUser() {
        return currentUser.getOrNull();
    }

    @ModelAttribute("settings")
    public Map<String, String> settings() {
        return settings.all();
    }

    /** Lưu ý: không nhận User qua tham số @ModelAttribute (sẽ bị data-binding từ request vào entity). */
    @ModelAttribute("cartCount")
    public int cartCount(Model model) {
        return model.getAttribute("currentUser") instanceof User u && !u.isCustomer() ? 0 : cart.count();
    }

    @ModelAttribute("unreadCount")
    public long unreadCount(Model model) {
        if (!(model.getAttribute("currentUser") instanceof User u)) return 0;
        return em.createQuery("select count(n) from UserNotification n where n.userId = :u and n.seen = false", Long.class)
                .setParameter("u", u.getId()).getSingleResult();
    }

    /** Id sản phẩm trong danh sách yêu thích của khách (để tô trái tim trên thẻ sản phẩm). */
    @ModelAttribute("wishlistIds")
    public Set<Long> wishlistIds(Model model) {
        if (!(model.getAttribute("currentUser") instanceof User u) || !u.isCustomer()) return Set.of();
        return new HashSet<>(em.createQuery("select w.product.id from WishlistItem w where w.userId = :u", Long.class)
                .setParameter("u", u.getId()).getResultList());
    }

    @ModelAttribute("navCategories")
    public List<Category> navCategories() {
        return categories.tree();
    }

    @ModelAttribute("footerPages")
    public List<StaticPage> footerPages() {
        try {
            return em.createQuery("select p from StaticPage p where p.published = true and p.showInFooter = true order by p.sortOrder, p.id", StaticPage.class)
                    .getResultList();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Dữ liệu form đã nhập lần trước (old()) - flash attribute "old" ghi đè khi có lỗi. */
    @ModelAttribute("old")
    public Map<String, String> old() {
        return Map.of();
    }

    @ModelAttribute("errors")
    public List<String> errors() {
        return List.of();
    }

    /* ============================ Lỗi ============================ */

    @ExceptionHandler(ValidationException.class)
    public Object validation(ValidationException e, HttpServletRequest req, HttpServletResponse res) {
        if (wantsJson(req)) {
            return ResponseEntity.status(422).body(Map.of("ok", false, "message", e.getErrors().isEmpty() ? e.getMessage() : e.getErrors().get(0),
                    "errors", e.getErrors()));
        }
        String back = sameHostReferer(req);
        FlashMap flash = RequestContextUtils.getOutputFlashMap(req);
        flash.put("errors", e.getErrors());
        flash.put("old", oldInput(req));
        return "redirect:" + (back != null ? back : "/");
    }

    @ExceptionHandler(BusinessException.class)
    public Object business(BusinessException e, HttpServletRequest req, HttpServletResponse res) {
        if (wantsJson(req)) {
            return ResponseEntity.status(e.getStatus()).body(Map.of("ok", false, "message", e.getMessage()));
        }
        String back = sameHostReferer(req);
        // Quay lại form kèm dữ liệu đã nhập (chuyển hướng sau POST luôn là GET nên không thể lặp)
        if (!"GET".equalsIgnoreCase(req.getMethod()) && back != null) {
            FlashMap flash = RequestContextUtils.getOutputFlashMap(req);
            flash.put("error", e.getMessage());
            flash.put("old", oldInput(req));
            if (e instanceof RxReuseException) flash.put("rx_reuse", true);
            return "redirect:" + back;
        }
        return errorPage(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public Object tooLarge(HttpServletRequest req, HttpServletResponse res) {
        return business(new BusinessException("Ảnh vượt quá dung lượng cho phép (5MB)."), req, res);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Object notFound(HttpServletRequest req) {
        if (wantsJson(req)) return ResponseEntity.status(404).body(Map.of("ok", false, "message", "Không tìm thấy."));
        return errorPage(404, null);
    }

    @ExceptionHandler(org.springframework.web.bind.MissingServletRequestParameterException.class)
    public Object missingParam(org.springframework.web.bind.MissingServletRequestParameterException e, HttpServletRequest req, HttpServletResponse res) {
        return business(new BusinessException("Thiếu thông tin: " + e.getParameterName() + "."), req, res);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public Object badParam(HttpServletRequest req, HttpServletResponse res) {
        return business(BusinessException.notFound(), req, res);
    }

    public static ModelAndView errorPage(int status, String message) {
        ModelAndView mv = new ModelAndView("errors/page");
        mv.addObject("status", status);
        mv.addObject("message", message);
        mv.setStatus(org.springframework.http.HttpStatus.valueOf(status));
        return mv;
    }

    public static boolean wantsJson(HttpServletRequest req) {
        String accept = req.getHeader("Accept");
        return (accept != null && accept.contains("application/json")) || "XMLHttpRequest".equals(req.getHeader("X-Requested-With"));
    }

    private static Map<String, String> oldInput(HttpServletRequest req) {
        Map<String, String> old = new HashMap<>();
        req.getParameterMap().forEach((k, v) -> {
            if (!NO_OLD.contains(k) && v.length > 0) old.put(k, v[0]);
        });
        return old;
    }

    /** Chỉ quay lại trang trước nếu cùng host (tránh open redirect). */
    public static String sameHostReferer(HttpServletRequest req) {
        String ref = req.getHeader("Referer");
        if (ref == null) return null;
        try {
            URI u = URI.create(ref);
            if (u.getHost() != null && !u.getHost().equalsIgnoreCase(req.getServerName())) return null;
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            return path + (u.getRawQuery() != null ? "?" + u.getRawQuery() : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
