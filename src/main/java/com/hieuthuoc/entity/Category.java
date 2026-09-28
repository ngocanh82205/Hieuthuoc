package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 120)
    private String slug;

    /** Tên icon Bootstrap Icons, VD: bi-capsule */
    @Column(length = 50)
    private String icon = "bi-capsule";

    private int sortOrder;

    /** Danh mục cha (danh mục đa cấp, VD: Thuốc > Tim mạch > Huyết áp). */
    @ManyToOne(fetch = FetchType.LAZY)
    private Category parent;

    /** SEO */
    @Column(length = 300)
    private String metaDescription;

    /** Cấp trong cây (0 = gốc) - được CategoryService điền khi dựng cây. */
    @Transient
    private int depth;

    @Transient
    private boolean hasChildren;
}
