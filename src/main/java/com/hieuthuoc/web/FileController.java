package com.hieuthuoc.web;

import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.MessageRepository;
import com.hieuthuoc.repository.PrescriptionRepository;
import com.hieuthuoc.service.BusinessException;
import com.hieuthuoc.service.CurrentUser;
import com.hieuthuoc.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.file.Path;

/** Phục vụ ảnh đơn thuốc / ảnh chat - chỉ chủ sở hữu hoặc dược sĩ/admin được xem. */
@Controller
@RequiredArgsConstructor
public class FileController {
    private final FileStorageService files;
    private final PrescriptionRepository prescriptionRepo;
    private final MessageRepository messageRepo;
    private final CurrentUser currentUser;

    @GetMapping("/files/prescriptions/{name:.+}")
    public ResponseEntity<Resource> prescription(@PathVariable String name) {
        User u = currentUser.get();
        Long owner = prescriptionRepo.findFirstByImage(name).map(p -> p.getUser().getId()).orElse(null);
        return serve(FileStorageService.Kind.PRESCRIPTIONS, name, owner != null && (u.isStaff() || owner.equals(u.getId())));
    }

    @GetMapping("/files/chat/{name:.+}")
    public ResponseEntity<Resource> chat(@PathVariable String name) {
        User u = currentUser.get();
        Long owner = messageRepo.findFirstByImage(name).map(m -> m.getConversation().getCustomer().getId()).orElse(null);
        return serve(FileStorageService.Kind.CHAT, name, owner != null && (u.isStaff() || owner.equals(u.getId())));
    }

    private ResponseEntity<Resource> serve(FileStorageService.Kind kind, String name, boolean allowed) {
        Path p = allowed ? files.resolve(kind, name) : null;
        if (p == null) throw BusinessException.notFound("Không tìm thấy file.");
        MediaType type = MediaTypeFactory.getMediaType(p.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noStore().cachePrivate())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'")
                .body(new FileSystemResource(p));
    }
}
