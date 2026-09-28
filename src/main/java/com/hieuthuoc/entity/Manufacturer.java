package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Thương hiệu / nhà sản xuất. */
@Entity
@Table(name = "manufacturers")
@Getter
@Setter
@NoArgsConstructor
public class Manufacturer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 150)
    private String name;

    @Column(length = 80)
    private String country;

    @Column(length = 200)
    private String website;

    @Column(length = 500)
    private String note;

    public Manufacturer(String name, String country) {
        this.name = name;
        this.country = country;
    }
}
