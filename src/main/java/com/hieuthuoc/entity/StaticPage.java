package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Trang tĩnh: giới thiệu, chính sách đổi trả, bảo mật, giao hàng... */
@Entity
@Table(name = "static_pages")
@Getter
@Setter
@NoArgsConstructor
public class StaticPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 20000)
    private String content;

    @Column(length = 300)
    private String metaDescription;

    private boolean published = true;

    /** Hiện link ở chân trang. */
    private boolean showInFooter = true;

    private int sortOrder;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public StaticPage(String slug, String title, String content, int sortOrder) {
        this.slug = slug;
        this.title = title;
        this.content = content;
        this.sortOrder = sortOrder;
    }

    /** Đoạn văn (cách nhau dòng trống); dòng bắt đầu bằng "## " là tiêu đề mục. */
    public List<String> getParagraphs() {
        return content == null ? List.of() : Arrays.stream(content.split("\\r?\\n\\s*\\r?\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
