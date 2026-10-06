package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/** Bài viết sức khỏe. */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
public class Post extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, unique = true, length = 220)
    private String slug;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false, columnDefinition = "longtext")
    private String content;

    @Column(length = 300)
    private String image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Column(nullable = false)
    private boolean published = true;

    public List<String> paragraphs() {
        if (content == null) return List.of();
        return Arrays.stream(content.split("\\r?\\n\\s*\\r?\\n|\\r?\\n")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public String imageUrl() {
        if (image == null || image.isEmpty()) return null;
        return image.startsWith("http") ? image : "/storage/" + image;
    }
}
