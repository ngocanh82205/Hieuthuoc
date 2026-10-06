package com.hieuthuoc.service;

import com.hieuthuoc.entity.PasswordResetRequest;
import com.hieuthuoc.entity.Role;
import com.hieuthuoc.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final NotificationService notifications;
    private final PasswordEncoder passwordEncoder;

    @PersistenceContext
    private EntityManager em;

    /** Tìm tài khoản theo email hoặc số điện thoại. */
    @Transactional(readOnly = true)
    public User findByLogin(String login) {
        String s = Texts.trim(login);
        if (s.isEmpty()) return null;
        List<User> l = s.contains("@")
                ? em.createQuery("select u from User u where lower(u.email) = :v", User.class).setParameter("v", s.toLowerCase()).setMaxResults(1).getResultList()
                : em.createQuery("select u from User u where u.phone = :v", User.class).setParameter("v", s.replaceAll("\\s+", "")).setMaxResults(1).getResultList();
        return l.isEmpty() ? null : l.get(0);
    }

    public User register(String fullName, String email, String phone, String password) {
        User u = new User();
        u.setRole(Role.CUSTOMER);
        u.setFullName(Texts.trim(fullName, 100));
        u.setEmail(email != null && !email.isBlank() ? email.trim().toLowerCase() : null);
        u.setPhone(Texts.trim(phone));
        u.setPassword(passwordEncoder.encode(password));
        em.persist(u);
        notifications.log(u, "auth.register", u.getEmail() != null ? u.getEmail() : u.getPhone());
        return u;
    }

    public boolean checkPassword(User u, String raw) {
        return raw != null && u.getPassword() != null && passwordEncoder.matches(raw, u.getPassword());
    }

    public void changePassword(User u, String current, String password, String confirm) {
        if (current == null || current.isEmpty() || !checkPassword(u, current)) throw new BusinessException("Mật khẩu hiện tại không đúng.");
        if (password == null || password.length() < 6) throw new BusinessException("Mật khẩu mới tối thiểu 6 ký tự.");
        if (!password.equals(confirm)) throw new BusinessException("Mật khẩu nhập lại không khớp.");
        User m = em.find(User.class, u.getId());
        m.setPassword(passwordEncoder.encode(password));
        notifications.log(m, "auth.change_password", null);
    }

    /** Khách quên mật khẩu: tạo yêu cầu để admin gọi điện xác minh và cấp mật khẩu tạm. */
    public void requestPasswordReset(String identifier0, String contactPhone0, String note) {
        String identifier = Texts.trim(identifier0, 150);
        String contactPhone = Texts.trim(contactPhone0);
        if (Texts.mbLen(identifier) < 5) throw new BusinessException("Vui lòng nhập email hoặc số điện thoại đã đăng ký.");
        if (!Texts.isPhone(contactPhone)) throw new BusinessException("Vui lòng nhập số điện thoại liên hệ hợp lệ để nhà thuốc gọi xác minh.");
        PasswordResetRequest r = new PasswordResetRequest();
        r.setIdentifier(identifier);
        r.setContactPhone(contactPhone);
        r.setNote(note != null && !note.isBlank() ? Texts.trim(note, 300) : null);
        r.setUser(findByLogin(identifier));
        em.persist(r);
        notifications.notifyAdmins("Yêu cầu quên mật khẩu: " + identifier + " (liên hệ " + contactPhone + ")", "/admin/password-resets");
    }

    /** Admin cấp mật khẩu tạm (hiển thị một lần để báo cho khách qua điện thoại). */
    public String resetPassword(User target, User admin) {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder pw = new StringBuilder();
        for (int i = 0; i < 8; i++) pw.append(chars.charAt(RANDOM.nextInt(chars.length())));
        target.setPassword(passwordEncoder.encode(pw.toString()));
        notifications.notify(target, "Mật khẩu của bạn đã được nhà thuốc đặt lại. Hãy đổi mật khẩu mới sau khi đăng nhập.", "/account#password");
        notifications.log(admin, "user.reset_password", target.getEmail() != null ? target.getEmail() : target.getPhone());
        return pw.toString();
    }

    public static String homeFor(User u) {
        return u.isStaff() ? "/staff" : "/";
    }
}
