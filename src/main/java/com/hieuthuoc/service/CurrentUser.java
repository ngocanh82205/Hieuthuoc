package com.hieuthuoc.service;

import com.hieuthuoc.config.AppUserDetails;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Lấy thực thể User của người đang đăng nhập. */
@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserRepository userRepo;

    public User getOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AppUserDetails d)) return null;
        return userRepo.findById(d.getId()).orElse(null);
    }

    public User get() {
        User u = getOrNull();
        if (u == null) throw new BusinessException("Vui lòng đăng nhập.", 401);
        return u;
    }
}
