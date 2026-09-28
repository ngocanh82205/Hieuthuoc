package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Danh mục hoạt chất (dùng gợi ý khi nhập sản phẩm, tra cứu thuốc cùng hoạt chất, cảnh báo tương tác). */
@Entity
@Table(name = "ingredients")
@Getter
@Setter
@NoArgsConstructor
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    /** Nhóm dược lý, VD: Giảm đau hạ sốt, Kháng sinh nhóm beta-lactam. */
    @Column(length = 150)
    private String drugGroup;

    @Column(length = 500)
    private String note;

    public Ingredient(String name) {
        this.name = name;
    }
}
