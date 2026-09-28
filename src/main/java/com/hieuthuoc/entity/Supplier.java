package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "suppliers")
@Getter
@Setter
@NoArgsConstructor
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 300)
    private String address;

    @Column(length = 20)
    private String taxCode;

    /** Hạn thanh toán công nợ (ngày kể từ khi phiếu nhập được duyệt). */
    private Integer paymentTermDays;

    public int getTermDays() {
        return paymentTermDays == null ? 30 : paymentTermDays;
    }
}
