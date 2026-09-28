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
        if (!Texts.isEmail(Texts.trim(f.getEmail()))) errors.add("Email không hợp lệ.");
        else if (userRepo.existsByEmailIgnoreCase(f.getEmail().trim())) errors.add("Email đã được sử dụng.");
        if (!Texts.isPhone(Texts.trim(f.getPhone()))) errors.add("Số điện thoại không hợp lệ (10-11 số, bắt đầu bằng 0).");
        if (f.getPassword() == null || f.getPassword().length() < 6) errors.add("Mật khẩu tối thiểu 6 ký tự.");
        else if (!f.getPassword().equals(f.getPasswordConfirm())) errors.add("Mật khẩu nhập lại không khớp.");
        return errors;
    }

    public User register(RegisterForm f) {
        User u = new User();
        u.setRole(Role.CUSTOMER);
        u.setFullName(Texts.trim(f.getFullName(), 100));
        u.setEmail(f.getEmail().trim().toLowerCase());
        u.setPhone(f.getPhone().trim());
        u.setPasswordHash(encoder.encode(f.getPassword()));
        userRepo.save(u);
        notifications.log(u, "auth.register", u.getEmail());
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

    public String encode(String raw) {
        return encoder.encode(raw);
    }
}
