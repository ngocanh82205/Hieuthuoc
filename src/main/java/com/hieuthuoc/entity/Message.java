package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tin nhắn tư vấn. kind: null (người gửi), AI (trợ lý AI), SYSTEM (thông báo hệ thống). */
@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class Message extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(name = "sender_id", insertable = false, updatable = false)
    private Long senderId;

    @Column(length = 10)
    private String kind;

    @Column(length = 2000)
    private String body;

    @Column(length = 200)
    private String image;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "suggested_cart_id")
    private SuggestedCart suggestedCart;

    public boolean isAi() {
        return "AI".equals(kind);
    }

    public boolean isSystem() {
        return "SYSTEM".equals(kind);
    }
}
