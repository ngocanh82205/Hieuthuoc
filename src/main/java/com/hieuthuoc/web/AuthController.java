package com.hieuthuoc.web;

import com.hieuthuoc.config.AppUserDetails;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Đăng nhập (POST /login do Spring Security xử lý - xem SecurityConfig), đăng ký, quên mật khẩu, Google. */
@Controller
@RequiredArgsConstructor
public class AuthController {
    private final AccountService accounts;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    /** Trang chỉ dành cho khách chưa đăng nhập (middleware guest). */
    private String guestOnly() {
        User u = currentUser.getOrNull();
        return u != null ? "redirect:" + AccountService.homeFor(u) : null;
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        String r = guestOnly();
        if (r != null) return r;
        model.addAttribute("title", "Đăng nhập");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        String r = guestOnly();
        if (r != null) return r;
        model.addAttribute("title", "Đăng ký");
        return "auth/register";
    }

    @PostMapping("/register")
    @Transactional
    public String register(@RequestParam MultiValueMap<String, String> params, HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        String r = guestOnly();
        if (r != null) return r;
        Form f = new Form(params);
        Validator.of(f)
                .required("full_name").min("full_name", 2).max("full_name", 100)
                .required("phone").phone("phone", "Số điện thoại không hợp lệ (10-11 số, bắt đầu bằng 0).")
                .unique("phone", v -> exists("phone", v), "Số điện thoại đã được đăng ký.")
                .email("email").max("email", 150).unique("email", v -> exists("email", v.toLowerCase()), "Email đã được sử dụng.")
                .required("password").min("password", 6).confirmed("password")
                .check();
        User u = accounts.register(f.get("full_name"), f.str("email"), f.get("phone"), f.get("password"));
        login(u, req, res);
        Web.success(ra, "Đăng ký thành công! Chào mừng bạn đến với nhà thuốc.");
        SavedRequest saved = new HttpSessionRequestCache().getRequest(req, res);
        return "redirect:" + (saved != null ? saved.getRedirectUrl() : "/");
    }

    private boolean exists(String column, String value) {
        String field = column.equals("phone") ? "u.phone" : "lower(u.email)";
        return em.createQuery("select count(u) from User u where " + field + " = :v", Long.class).setParameter("v", value).getSingleResult() > 0;
    }

    /** Đăng nhập ngay sau khi đăng ký (giữ nguyên phiên nên giỏ hàng của khách vãng lai vẫn còn). */
    private static void login(User u, HttpServletRequest req, HttpServletResponse res) {
        AppUserDetails d = new AppUserDetails(u);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(d, null, d.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        req.changeSessionId();
        new HttpSessionSecurityContextRepository().saveContext(ctx, req, res);
    }

    @GetMapping("/forgot-password")
    public String forgotForm(Model model) {
        String r = guestOnly();
        if (r != null) return r;
        model.addAttribute("title", "Quên mật khẩu");
        return "auth/forgot";
    }

    @PostMapping("/forgot-password")
    @Transactional
    public String forgot(@RequestParam(required = false) String identifier, @RequestParam(name = "contact_phone", required = false) String contactPhone,
                         @RequestParam(required = false) String note, RedirectAttributes ra) {
        String r = guestOnly();
        if (r != null) return r;
        accounts.requestPasswordReset(identifier, contactPhone, note);
        Web.success(ra, "Đã gửi yêu cầu. Nhà thuốc sẽ gọi điện xác minh và cấp mật khẩu tạm cho bạn trong giờ làm việc.");
        return "redirect:/login";
    }

    /** Đăng nhập Google: bản Spring chưa cấu hình OAuth (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET). */
    @GetMapping({"/auth/google", "/auth/google/callback"})
    public String google(RedirectAttributes ra) {
        Web.warning(ra, "Đăng nhập Google chưa được cấu hình (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET).");
        return "redirect:/login";
    }
}
