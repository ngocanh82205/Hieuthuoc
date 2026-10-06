package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Phiếu kiểm kê kho (lưu số sổ sách và số đếm thực tế của từng lô). */
@Entity
@Table(name = "stocktakes")
@Getter
@Setter
@NoArgsConstructor
public class Stocktake extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(length = 300)
    private String note;

    @Column(name = "total_lines", nullable = false)
    private int totalLines;

    @Column(name = "diff_lines", nullable = false)
    private int diffLines;

    @OneToMany(mappedBy = "stocktake", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productName ASC, expDate ASC")
    private List<StocktakeItem> items = new ArrayList<>();
}
