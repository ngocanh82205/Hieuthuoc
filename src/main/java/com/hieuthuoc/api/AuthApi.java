package com.hieuthuoc.api;

import com.hieuthuoc.entity.User;
import com.hieuthuoc.service.*;
import com.hieuthuoc.web.Validator;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Xác thực: đăng nhập lấy access token, đăng ký, quên mật khẩu, thông tin phiên, đổi mật khẩu. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthApi {
    private final AccountService accounts;
    private final TokenService tokens;
    private final NotificationService notifications;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    private Dto.TokenDto token(User u) {
        return new Dto.TokenDto(tokens.issue(u), "Bearer", tokens.ttlSeconds(), Dto.UserDto.of(u));
    }

    /** Body: {"login": "email hoặc SĐT", "password": "..."} */
    @PostMapping("/login")
    @Transactional
    public Map<String, Object> login(@RequestBody Map<String, Object> body, HttpServletRequest req) {
        Form f = Api.form(body);
        Validator.of(f).label("login", "Email / số điện thoại").required("login").required("password").check();
        User u = accounts.findByLogin(f.str("login"));
        if (u == null || !accounts.checkPassword(u, f.get("password"))) throw new BusinessException("Email/số điện thoại hoặc mật khẩu không đúng.", 401);
        if (u.isLocked()) throw new BusinessException("Tài khoản đã bị khóa. Vui lòng liên hệ nhà thuốc.", 403);
        notifications.log(u, "auth.login", "API " + req.getRemoteAddr());
        return Api.ok(token(u), "Đăng nhập thành công.");
    }

    /** Body: {"fullName", "email", "phone", "password", "passwordConfirmation"} - tạo tài khoản khách hàng. */
    @PostMapping("/register")
    @Transactional
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, Object> body) {
        Form f = Api.form(body);
        Validator.of(f)
                .label("fullName", "Họ tên").label("passwordConfirmation", "Xác nhận mật khẩu")
                .required("fullName").min("fullName", 2).max("fullName", 100)
                .required("phone").phone("phone", "Số điện thoại không hợp lệ.")
                .unique("phone", v -> taken("u.phone", v), "Số điện thoại đã được đăng ký.")
                .email("email").unique("email", v -> taken("lower(u.email)", v.toLowerCase()), "Email đã được đăng ký.")
                .required("password").min("password", 6)
                .rule("passwordConfirmation", f.get("password") != null && f.get("password").equals(f.get("passwordConfirmation")), "Xác nhận mật khẩu không khớp.")
                .check();
        User u = accounts.register(f.str("fullName"), f.str("email"), f.str("phone"), f.get("password"));
        return Api.created(token(u), "Đăng ký thành công.");
    }

    private boolean taken(String field, String value) {
        return em.createQuery("select count(u) from User u where " + field + " = :v", Long.class).setParameter("v", value).getSingleResult() > 0;
    }

    /** Body: {"identifier": "email/SĐT tài khoản", "contactPhone": "SĐT liên hệ", "note"} - gửi yêu cầu cho quản trị viên xác minh. */
    @PostMapping("/forgot-password")
    @Transactional
    public Map<String, Object> forgot(@RequestBody Map<String, Object> body) {
        Form f = Api.form(body);
        accounts.requestPasswordReset(f.get("identifier"), f.get("contactPhone"), f.get("note"));
        return Api.ok(null, "Đã gửi yêu cầu. Nhà thuốc sẽ gọi lại số liên hệ để xác minh và cấp mật khẩu tạm.");
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public Map<String, Object> me() {
        return Api.ok(Dto.UserDto.of(currentUser.get()));
    }

    /** Body: {"current", "password", "passwordConfirmation"} */
    @PostMapping("/change-password")
    @Transactional
    public Map<String, Object> changePassword(@RequestBody Map<String, Object> body) {
        Form f = Api.form(body);
        accounts.changePassword(currentUser.get(), f.get("current"), f.get("password"), f.get("passwordConfirmation"));
        return Api.ok(null, "Đổi mật khẩu thành công.");
    }

    /** Token phi trạng thái: client chỉ cần xóa token; endpoint ghi nhật ký đăng xuất. */
    @PostMapping("/logout")
    @Transactional
    public Map<String, Object> logout() {
        notifications.log(currentUser.get(), "auth.logout", "API");
        return Api.ok(null, "Đã đăng xuất.");
    }
}
