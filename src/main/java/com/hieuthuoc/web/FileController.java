package com.hieuthuoc.web;

import com.hieuthuoc.entity.*;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.CurrentUser;
import com.hieuthuoc.service.FileStorageService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Hiển thị file riêng tư (đơn thuốc, ảnh chat), kiểm tra quyền theo vai trò và quyền hạn (RBAC) để chống rò rỉ dữ liệu y tế.
 */
@Controller
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService files;
    private final CurrentUser currentUser;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/files/{type}/{name:.+}")
    @Transactional(readOnly = true)
    public ResponseEntity<FileSystemResource> show(@PathVariable String type, @PathVariable String name) {
        User u = currentUser.get();
        String file = Path.of(name).getFileName().toString();
        if (!FileStorageService.PRIVATE_KINDS.contains(type)) throw BusinessException.notFound("Không tìm thấy file.");
        boolean allowed = switch (type) {
            case "prescriptions" -> authorizePrescription(u, file);
            case "chat" -> authorizeChat(u, file);
            default -> false;
        };
        if (!allowed) throw BusinessException.forbidden("Bạn không có quyền truy cập file này.");
        Path path = files.privatePath(type, file);
        if (path == null) throw BusinessException.notFound("Không tìm thấy file.");
        MediaType mt = MediaTypeFactory.getMediaType(path.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mt)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .body(new FileSystemResource(path));
    }

    private boolean authorizePrescription(User user, String name) {
        Prescription rx = em.createQuery("select p from Prescription p left join fetch p.order where p.image = :a or p.image = :b", Prescription.class)
                .setParameter("a", name).setParameter("b", "prescriptions/" + name).setMaxResults(1).getResultStream().findFirst()
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy đơn thuốc."));
        // 1. Chủ sở hữu đơn thuốc hoặc chủ đơn hàng tương ứng
        if (Objects.equals(rx.getUser().getId(), user.getId()) || (rx.getOrder() != null && Objects.equals(rx.getOrder().getUser().getId(), user.getId()))) return true;
        // 2. Quản trị viên, 3. nhân viên có quyền duyệt đơn thuốc
        return user.isAdmin() || user.hasPermission(StaffPermission.RX_REVIEW);
    }

    private boolean authorizeChat(User user, String name) {
        Message msg = em.createQuery("select m from Message m join fetch m.conversation where m.image = :a or m.image = :b", Message.class)
                .setParameter("a", name).setParameter("b", "chat/" + name).setMaxResults(1).getResultStream().findFirst()
                .orElseThrow(() -> BusinessException.notFound("Không tìm thấy tin nhắn chat."));
        Conversation conv = msg.getConversation();
        // 1. Khách hàng sở hữu cuộc trò chuyện, 2. quản trị viên
        if (Objects.equals(conv.getCustomer().getId(), user.getId()) || user.isAdmin()) return true;
        // 3. Nhân viên có quyền CONSULT: chỉ người phụ trách hoặc hội thoại chưa có ai tiếp nhận
        if (!user.hasPermission(StaffPermission.CONSULT)) return false;
        return conv.getPharmacist() == null || Objects.equals(conv.getPharmacist().getId(), user.getId());
    }
}
