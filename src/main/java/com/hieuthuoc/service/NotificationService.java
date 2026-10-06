package com.hieuthuoc.service;

import com.hieuthuoc.config.AppUserDetails;
import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.AuditLogRepository;
import com.hieuthuoc.repository.UserNotificationRepository;
import com.hieuthuoc.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

/** Thông báo trong hệ thống + nhật ký thao tác (audit log). */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {
    private final UserNotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final AuditLogRepository auditRepo;

    @PersistenceContext
    private EntityManager em;

    public UserNotification notify(User user, String message, String link) {
        return notify(user, message, link, false);
    }

    public UserNotification notify(User user, String message, String link, boolean popup) {
        UserNotification n = new UserNotification();
        n.setUser(user);
        n.setMessage(Texts.limit(message, 497));
        n.setLink(link);
        n.setPopup(popup);
        return notificationRepo.save(n);
    }

    public void notifyStaff(String message, String link) {
        for (User u : userRepo.findByRoleInAndLockedFalse(List.of(Role.PHARMACIST, Role.ADMIN))) notify(u, message, link);
    }

    public void notifyAdmins(String message, String link) {
        for (User u : userRepo.findByRoleInAndLockedFalse(List.of(Role.ADMIN))) notify(u, message, link);
    }

    /** Thông báo cho nhân viên có quyền (và admin). */
    public void notifyPermission(String permission, String message, String link) {
        StaffPermission p = StaffPermission.valueOf(permission);
        for (User u : userRepo.findByRoleInAndLockedFalse(List.of(Role.PHARMACIST, Role.ADMIN))) {
            if (u.hasPermission(p)) notify(u, message, link);
        }
    }

    public void log(User user, String action, String detail) {
        AuditLog log = new AuditLog();
        if (user != null) {
            log.setUser(user);
        } else {
            Long id = currentUserId();
            if (id != null) log.setUser(em.getReference(User.class, id));
        }
        log.setAction(action);
        log.setDetail(detail != null ? Texts.limit(detail, 997) : null);
        log.setIp(clientIp());
        auditRepo.save(log);
    }

    private static Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AppUserDetails d ? d.getId() : null;
    }

    private static String clientIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest req = attrs.getRequest();
        String ip = req.getRemoteAddr();
        return "0:0:0:0:0:0:0:1".equals(ip) ? "::1" : ip;
    }
}
