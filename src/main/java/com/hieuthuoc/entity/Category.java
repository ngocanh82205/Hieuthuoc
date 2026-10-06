package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    @Column(nullable = false, length = 50)
    private String icon = "bi-capsule";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Category parent;

    @Column(name = "parent_id", insertable = false, updatable = false)
    private Long parentId;

    @Column(name = "meta_description", length = 300)
    private String metaDescription;

    @OneToMany(mappedBy = "parent")
    @OrderBy("sortOrder ASC, id ASC")
    private List<Category> children = new ArrayList<>();

    /** Độ sâu trong cây (không lưu DB, CategoryService điền vào). */
    @Transient
    private int depth;

    @Transient
    private boolean hasChildren;
}
