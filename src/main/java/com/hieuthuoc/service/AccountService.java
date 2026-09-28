package com.hieuthuoc.service;

import com.hieuthuoc.entity.Role;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.UserRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class AccountService {
    private final UserRepository userRepo;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final com.hieuthuoc.repository.PasswordResetRequestRepository resetRepo;

    @Getter
    @Setter
    public static class RegisterForm {
        private String fullName;
        private String email;
        private String phone;
        private String password;
        private String passwordConfirm;
    }

    public List<String> validateRegister(RegisterForm f) {
        List<String> errors = new ArrayList<>();
        if (Texts.trim(f.getFullName()).length() < 2) errors.add("Vui lòng nhập họ tên.");
        // Đăng ký bằng số điện thoại; email không bắt buộc
        if (!Texts.isPhone(Texts.trim(f.getPhone()))) errors.add("Số điện thoại không hợp lệ (10-11 số, bắt đầu bằng 0).");
        else if (userRepo.existsByPhone(f.getPhone().trim())) errors.add("Số điện thoại đã được đăng ký.");
        if (!Texts.isBlank(f.getEmail())) {
            if (!Texts.isEmail(f.getEmail().trim())) errors.add("Email không hợp lệ.");
            else if (userRepo.existsByEmailIgnoreCase(f.getEmail().trim())) errors.add("Email đã được sử dụng.");
        }
        if (f.getPassword() == null || f.getPassword().length() < 6) errors.add("Mật khẩu tối thiểu 6 ký tự.");
        else if (!f.getPassword().equals(f.getPasswordConfirm())) errors.add("Mật khẩu nhập lại không khớp.");
        return errors;
    }

    public User register(RegisterForm f) {
        User u = new User();
        u.setRole(Role.CUSTOMER);
        u.setFullName(Texts.trim(f.getFullName(), 100));
        u.setEmail(Texts.isBlank(f.getEmail()) ? null : f.getEmail().trim().toLowerCase());
        u.setPhone(f.getPhone().trim());
        u.setPasswordHash(encoder.encode(f.getPassword()));
        userRepo.save(u);
        notifications.log(u, "auth.register", u.getEmail() != null ? u.getEmail() : u.getPhone());
        return u;
    }

    public void changePassword(User u, String current, String password, String confirm) {
        if (current == null || !encoder.matches(current, u.getPasswordHash())) throw new BusinessException("Mật khẩu hiện tại không đúng.");
        if (password == null || password.length() < 6) throw new BusinessException("Mật khẩu mới tối thiểu 6 ký tự.");
        if (!password.equals(confirm)) throw new BusinessException("Mật khẩu nhập lại không khớp.");
        u.setPasswordHash(encoder.encode(password));
        userRepo.save(u);
        notifications.log(u, "auth.change_password", null);
    }

    /** Tìm tài khoản theo email hoặc số điện thoại (SĐT phải là duy nhất). */
    public java.util.Optional<User> findByLogin(String login) {
        String s = Texts.trim(login);
        if (s.contains("@")) return userRepo.findByEmailIgnoreCase(s);
        List<User> list = userRepo.findByPhone(s);
        return list.size() == 1 ? java.util.Optional.of(list.get(0)) : java.util.Optional.empty();
    }

    /** Khách quên mật khẩu: tạo yêu cầu để admin xác minh qua điện thoại và cấp mật khẩu tạm. */
    public void requestPasswordReset(String identifier, String contactPhone, String note) {
        identifier = Texts.trim(identifier, 150);
        contactPhone = Texts.trim(contactPhone);
        if (identifier.length() < 5) throw new BusinessException("Vui lòng nhập email hoặc số điện thoại đã đăng ký.");
        if (!Texts.isPhone(contactPhone)) throw new BusinessException("Vui lòng nhập số điện thoại liên hệ hợp lệ để nhà thuốc gọi xác minh.");
        com.hieuthuoc.entity.PasswordResetRequest r = new com.hieuthuoc.entity.PasswordResetRequest();
        r.setIdentifier(identifier);
        r.setContactPhone(contactPhone);
        r.setNote(Texts.emptyToNull(Texts.trim(note, 300)));
        r.setUser(findByLogin(identifier).orElse(null));
        resetRepo.save(r);
        notifications.notifyAdmins("Yêu cầu quên mật khẩu: " + identifier + " (liên hệ " + contactPhone + ")", "/admin/password-resets");
    }

    /** Admin cấp mật khẩu tạm (hiển thị một lần để báo cho khách qua điện thoại). */
    public String resetPassword(User target, User admin) {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) sb.append(chars.charAt(rnd.nextInt(chars.length())));
        target.setPasswordHash(encoder.encode(sb.toString()));
        userRepo.save(target);
        notifications.notify(target, "Mật khẩu của bạn đã được nhà thuốc đặt lại. Hãy đổi mật khẩu mới sau khi đăng nhập.", "/account#password");
        notifications.log(admin, "user.reset_password", target.getEmail() != null ? target.getEmail() : target.getPhone());
        return sb.toString();
    }

    public String encode(String raw) {
        return encoder.encode(raw);
    }
}
