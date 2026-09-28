package com.hieuthuoc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu file upload. Ảnh đơn thuốc và ảnh chat là dữ liệu sức khỏe nhạy cảm nên được lưu
 * ngoài thư mục static và chỉ phục vụ qua controller có kiểm tra quyền.
 */
@Service
public class FileStorageService {
    public enum Kind { PRESCRIPTIONS, CHAT, PRODUCTS, BANNERS }

    private static final Map<String, String> ALLOWED = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp", "image/gif", ".gif");
    private static final long MAX_SIZE = 5L * 1024 * 1024;

    private final Path root;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        for (Kind k : Kind.values()) Files.createDirectories(dir(k));
    }

    public Path dir(Kind kind) {
        return root.resolve(kind.name().toLowerCase());
    }

    public boolean isPresent(MultipartFile file) {
        return file != null && !file.isEmpty();
    }

    public String store(Kind kind, MultipartFile file) {
        if (!isPresent(file)) throw new BusinessException("Vui lòng chọn ảnh.");
        String ext = ALLOWED.get(file.getContentType());
        if (ext == null) throw new BusinessException("Chỉ chấp nhận ảnh JPG, PNG, WEBP hoặc GIF.");
        if (file.getSize() > MAX_SIZE) throw new BusinessException("Ảnh vượt quá dung lượng 5MB.");
        String name = UUID.randomUUID().toString().replace("-", "") + ext;
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, dir(kind).resolve(name), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BusinessException("Không lưu được file, vui lòng thử lại.", 500);
        }
        return name;
    }

    /** Đường dẫn an toàn tới file (chặn path traversal). */
    public Path resolve(Kind kind, String filename) {
        Path dir = dir(kind);
        Path p = dir.resolve(Path.of(filename).getFileName().toString()).normalize();
        if (!p.startsWith(dir) || !Files.isRegularFile(p)) return null;
        return p;
    }

    public void writeSample(Kind kind, String filename, byte[] content) {
        try {
            Files.write(dir(kind).resolve(filename), content);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
