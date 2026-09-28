package com.hieuthuoc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Sao lưu dữ liệu: với H2 dùng lệnh SCRIPT xuất toàn bộ CSDL ra file SQL nén (.zip); tự sao lưu hằng ngày, giữ 7 bản gần nhất.
 * Với MySQL dùng mysqldump (hướng dẫn trên trang Sao lưu).
 */
@Slf4j
@Service
public class BackupService {
    public record BackupFile(String name, long size, LocalDateTime modified) {
    }

    private final DataSource dataSource;
    private final Path dir;

    public BackupService(DataSource dataSource, @Value("${app.backup-dir:backups}") String dir) {
        this.dataSource = dataSource;
        this.dir = Path.of(dir).toAbsolutePath().normalize();
    }

    public Path dir() {
        return dir;
    }

    public String databaseName() {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getDatabaseProductName() + " " + c.getMetaData().getDatabaseProductVersion();
        } catch (SQLException e) {
            return "?";
        }
    }

    public boolean isSupported() {
        return databaseName().toUpperCase().startsWith("H2");
    }

    /** Tạo bản sao lưu, trả về tên file. */
    public String create() {
        if (!isSupported()) throw new BusinessException("CSDL hiện tại không phải H2 - hãy sao lưu bằng mysqldump (xem hướng dẫn trên trang).");
        String name = "vinapharma_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip";
        try {
            Files.createDirectories(dir);
            Path target = dir.resolve(name);
            Path sql = Files.createTempFile(dir, "backup", ".sql");
            try {
                try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
                    st.execute("SCRIPT TO '" + sql.toString().replace("'", "''") + "'");
                }
                try (java.util.zip.ZipOutputStream zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(target))) {
                    zip.putNextEntry(new java.util.zip.ZipEntry(name.replace(".zip", ".sql")));
                    Files.copy(sql, zip);
                    zip.closeEntry();
                }
            } finally {
                Files.deleteIfExists(sql);
            }
            return name;
        } catch (IOException | SQLException e) {
            throw new BusinessException("Không tạo được bản sao lưu: " + e.getMessage());
        }
    }

    public List<BackupFile> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().endsWith(".zip")).map(p -> {
                try {
                    return new BackupFile(p.getFileName().toString(), Files.size(p),
                            LocalDateTime.ofInstant(Files.getLastModifiedTime(p).toInstant(), java.time.ZoneId.systemDefault()));
                } catch (IOException e) {
                    return new BackupFile(p.getFileName().toString(), 0, null);
                }
            }).sorted(Comparator.comparing(BackupFile::name).reversed()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** File sao lưu theo tên (chặn truy cập ngoài thư mục backup). */
    public Path file(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]+\\.zip")) throw BusinessException.notFound("Không tìm thấy bản sao lưu.");
        Path f = dir.resolve(name).normalize();
        if (!f.startsWith(dir) || !Files.isRegularFile(f)) throw BusinessException.notFound("Không tìm thấy bản sao lưu.");
        return f;
    }

    /** Tự động sao lưu 2h30 sáng mỗi ngày, giữ 7 bản gần nhất. */
    @Scheduled(cron = "0 30 2 * * *")
    public void daily() {
        if (!isSupported()) return;
        try {
            create();
            List<BackupFile> files = list();
            for (int i = 7; i < files.size(); i++) Files.deleteIfExists(dir.resolve(files.get(i).name()));
        } catch (Exception e) {
            log.warn("Sao lưu tự động thất bại: {}", e.getMessage());
        }
    }
}
