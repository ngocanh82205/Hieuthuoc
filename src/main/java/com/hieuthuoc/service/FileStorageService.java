package com.hieuthuoc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu file upload, dùng chung thư mục storage/app của bản Laravel:
 * - Ảnh đơn thuốc, ảnh chat: storage/app/private/{kind} - KHÔNG public, chỉ chủ sở hữu hoặc nhân viên xem qua FileController.
 * - Ảnh sản phẩm, bài viết: storage/app/public/{kind}, phục vụ tại /storage/{kind}/{tên file}.
 */
@Service
public class FileStorageService {
    public static final List<String> PRIVATE_KINDS = List.of("prescriptions", "chat");

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private final Path privateRoot;
    private final Path publicRoot;

    public FileStorageService(@Value("${app.storage-root}") String storageRoot) {
        Path root = Path.of(storageRoot).toAbsolutePath().normalize();
        this.privateRoot = root.resolve("private");
        this.publicRoot = root.resolve("public");
    }

    public Path publicRoot() {
        return publicRoot;
    }

    public boolean isPresent(MultipartFile file) {
        return file != null && !file.isEmpty() && file.getSize() > 0;
    }

    /** @return tên file (private) hoặc đường dẫn tương đối trong disk public (products/xxx.jpg) */
    public String store(String kind, MultipartFile file) {
        if (!isPresent(file)) throw new BusinessException("Vui lòng chọn ảnh.");
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) throw new BusinessException("Chỉ chấp nhận ảnh JPG, PNG, WEBP hoặc GIF.");
        if (file.getSize() > MAX_SIZE) throw new BusinessException("Ảnh vượt quá dung lượng 5MB.");
        String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        boolean priv = PRIVATE_KINDS.contains(kind);
        Path dir = (priv ? privateRoot : publicRoot).resolve(kind);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(dir);
            Files.copy(in, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Không lưu được file, vui lòng thử lại.", 500);
        }
        return priv ? name : kind + "/" + name;
    }

    /** Đường dẫn tuyệt đối an toàn tới file private (chặn path traversal). */
    public Path privatePath(String kind, String name) {
        if (!PRIVATE_KINDS.contains(kind) || name == null) return null;
        Path dir = privateRoot.resolve(kind).normalize();
        Path p = dir.resolve(Path.of(name).getFileName().toString()).normalize();
        return p.startsWith(dir) && Files.isRegularFile(p) ? p : null;
    }

    public static String url(String kind, String name) {
        return name == null || name.isEmpty() ? null : "/files/" + kind + "/" + name;
    }
}
