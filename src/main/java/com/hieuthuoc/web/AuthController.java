package com.hieuthuoc.web;

import com.hieuthuoc.config.AppUserDetails;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.NotificationRepository;
import com.hieuthuoc.service.AccountService;
import com.hieuthuoc.service.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class AuthController {
    private final AccountService accountService;
    private final CurrentUser currentUser;
    private final NotificationRepository notificationRepo;
    private final SecurityContextRepository contextRepo;

    @GetMapping("/login")
    public String login(HttpServletRequest req, Model model) {
        User u = currentUser.getOrNull();
        if (u != null) return "redirect:" + home(u);
        if (req.getParameter("error") != null) {
            HttpSession s = req.getSession(false);
            Object ex = s == null ? null : s.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            model.addAttribute("error", ex instanceof LockedException
                    ? "Tài khoản đã bị khóa. Vui lòng liên hệ nhà thuốc."
                    : "Email hoặc mật khẩu không đúng.");
        }
        if (req.getParameter("locked") != null) model.addAttribute("error", "Tài khoản của bạn đã bị khóa.");
        model.addAttribute("title", "Đăng nhập");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        if (currentUser.getOrNull() != null) return "redirect:/";
        model.addAttribute("form", new AccountService.RegisterForm());
        model.addAttribute("title", "Đăng ký");
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") AccountService.RegisterForm form, Model model,
                           HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        List<String> errors = accountService.validateRegister(form);
        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("title", "Đăng ký");
            return "auth/register";
        }
        User u = accountService.register(form);
        // Tự động đăng nhập sau khi đăng ký
        AppUserDetails details = new AppUserDetails(u);
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        req.getSession(true);
        req.changeSessionId();
        contextRepo.saveContext(ctx, req, res);
        Flash.success(ra, "Đăng ký thành công! Chào mừng bạn đến với nhà thuốc.");
        return "redirect:/";
    }

    @GetMapping("/notifications")
    @Transactional
    public String notifications(Model model) {
        User u = currentUser.get();
        model.addAttribute("notifications", notificationRepo.findTop100ByUserOrderByIdDesc(u));
        notificationRepo.markAllSeen(u);
        model.addAttribute("unreadCount", 0L);
        model.addAttribute("title", "Thông báo");
        return u.isStaff() ? "staff/notifications" : "account/notifications";
    }

    @GetMapping("/403")
    public String forbidden(Model model, HttpServletResponse res) {
        res.setStatus(403);
        model.addAttribute("status", 403);
        model.addAttribute("message", "Bạn không có quyền truy cập chức năng này.");
        return "error";
    }

    static String home(User u) {
        return switch (u.getRole()) {
            case ADMIN -> "/admin";
            case PHARMACIST -> "/staff";
            default -> "/";
        };
    }
}
