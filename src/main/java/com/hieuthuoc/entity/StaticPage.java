package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/** Trang tĩnh (chính sách, giới thiệu...) hiển thị ở chân trang. */
@Entity
@Table(name = "static_pages")
@Getter
@Setter
@NoArgsConstructor
public class StaticPage extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "longtext")
    private String content;

    @Column(name = "meta_description", length = 300)
    private String metaDescription;

    @Column(nullable = false)
    private boolean published = true;

    @Column(name = "show_in_footer", nullable = false)
    private boolean showInFooter = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public List<String> paragraphs() {
        if (content == null) return List.of();
        return Arrays.stream(content.split("\\r?\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
