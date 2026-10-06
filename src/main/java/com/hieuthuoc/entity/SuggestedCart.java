package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/** Giỏ thuốc dược sĩ gợi ý trong cuộc tư vấn. */
@Entity
@Table(name = "suggested_carts")
@Getter
@Setter
@NoArgsConstructor
public class SuggestedCart extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacist_id")
    private User pharmacist;

    @Column(length = 500)
    private String note;

    @OneToMany(mappedBy = "suggestedCart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<SuggestedCartItem> items = new ArrayList<>();

    public long total() {
        return items.stream().mapToLong(i -> i.getPrice() * i.getQuantity()).sum();
    }
}
