package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Quy tắc cảnh báo tương tác giữa hai hoạt chất (admin cấu hình, dùng khi tư vấn / duyệt đơn). */
@Entity
@Table(name = "drug_interactions")
@Getter
@Setter
@NoArgsConstructor
public class DrugInteraction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Từ khóa hoạt chất A, B (so khớp không phân biệt hoa thường, chứa trong thành phần). */
    @Column(nullable = false, length = 100)
    private String ingredientA;

    @Column(nullable = false, length = 100)
    private String ingredientB;

    /** danger | warning | info */
    @Column(nullable = false, length = 10)
    private String level = "warning";

    @Column(nullable = false, length = 500)
    private String message;

    public DrugInteraction(String a, String b, String level, String message) {
        this.ingredientA = a;
        this.ingredientB = b;
        this.level = level;
        this.message = message;
    }

    public String getLevelLabel() {
        return switch (level) {
            case "danger" -> "Nghiêm trọng";
            case "info" -> "Lưu ý";
            default -> "Thận trọng";
        };
    }
}
