package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Giỏ hàng tư vấn: dược sĩ chọn sẵn sản phẩm và gửi cho khách qua chat. */
@Entity
@Table(name = "suggested_carts")
@Getter
@Setter
@NoArgsConstructor
public class SuggestedCart {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    private User pharmacist;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<SuggestedCartItem> items = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public long getTotal() {
        return items.stream().mapToLong(i -> i.getPrice() * i.getQuantity()).sum();
    }
}
