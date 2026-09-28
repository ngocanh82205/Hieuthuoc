package com.hieuthuoc.service;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.repository.AuditLogRepository;
import com.hieuthuoc.repository.NotificationRepository;
import com.hieuthuoc.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Thông báo trong hệ thống + nhật ký thao tác (audit log). */
@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepo;
    private final UserRepository userRepo;
    private final AuditLogRepository auditRepo;

    public void notify(User user, String message, String link) {
        Notification n = new Notification();
        n.setUser(user);
        n.setMessage(message.length() > 500 ? message.substring(0, 497) + "..." : message);
        n.setLink(link);
        notificationRepo.save(n);
    }

    public void notifyStaff(String message, String link) {
        for (User u : userRepo.findByRoleInAndLockedFalse(List.of(Role.PHARMACIST, Role.ADMIN))) notify(u, message, link);
    }

    public void notifyAdmins(String message, String link) {
        for (User u : userRepo.findByRoleInAndLockedFalse(List.of(Role.ADMIN))) notify(u, message, link);
    }

    public void log(User user, String action, String detail) {
        AuditLog log = new AuditLog();
        log.setUser(user);
        log.setAction(action);
        log.setDetail(detail != null && detail.length() > 1000 ? detail.substring(0, 1000) : detail);
        auditRepo.save(log);
    }
}
