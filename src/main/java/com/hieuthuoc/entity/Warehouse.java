package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Kho / chi nhánh. Chỉ tồn kho ở kho "được bán" mới tính vào hàng bán online và tại quầy. */
@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 300)
    private String address;

    /** Hàng trong kho này được bán (online + quầy). Kho dự trữ / chi nhánh khác = false. */
    private boolean sellable = true;

    /** Kho chính: lô không gán kho được hiểu là thuộc kho chính. */
    private boolean main;

    private boolean active = true;

    public Warehouse(String name, String address, boolean sellable, boolean main) {
        this.name = name;
        this.address = address;
        this.sellable = sellable;
        this.main = main;
    }
}
