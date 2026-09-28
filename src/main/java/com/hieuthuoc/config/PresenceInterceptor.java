package com.hieuthuoc.config;

import com.hieuthuoc.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

/** Ghi nhận thời điểm hoạt động của dược sĩ/admin (tối đa 1 lần/phút) để biết ai đang online. */
@Component
@RequiredArgsConstructor
public class PresenceInterceptor implements HandlerInterceptor {
    private final UserRepository userRepo;

    @Override
    @Transactional
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserDetails d && !"CUSTOMER".equals(d.getRole())) {
            userRepo.findById(d.getId()).ifPresent(u -> {
                if (u.getLastSeenAt() == null || u.getLastSeenAt().isBefore(LocalDateTime.now().minusMinutes(1))) {
                    u.setLastSeenAt(LocalDateTime.now());
                    userRepo.save(u);
                }
            });
        }
        return true;
    }
}
