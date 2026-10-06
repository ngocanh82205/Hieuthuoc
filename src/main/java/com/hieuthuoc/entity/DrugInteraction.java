package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cặp hoạt chất tương tác (level: danger / warning / info). */
@Entity
@Table(name = "drug_interactions")
@Getter
@Setter
@NoArgsConstructor
public class DrugInteraction extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_a", nullable = false, length = 100)
    private String ingredientA;

    @Column(name = "ingredient_b", nullable = false, length = 100)
    private String ingredientB;

    @Column(nullable = false, length = 10)
    private String level = "warning";

    @Column(nullable = false, length = 500)
    private String message;

    public String levelLabel() {
        return switch (level == null ? "" : level) {
            case "danger" -> "Nghiêm trọng";
            case "info" -> "Lưu ý";
            default -> "Thận trọng";
        };
    }
}
