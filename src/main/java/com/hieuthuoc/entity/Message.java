package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "messages", indexes = @Index(columnList = "conversation_id"))
@Getter
@Setter
@NoArgsConstructor
public class Message {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Conversation conversation;

    /** Người gửi; null với tin nhắn của trợ lý AI / hệ thống. */
    @ManyToOne(fetch = FetchType.LAZY)
    private User sender;

    /** null = tin người gửi; AI = trợ lý AI trả lời; SYSTEM = thông báo hệ thống (chuyển dược sĩ...). */
    @Column(length = 10)
    private String kind;

    @Column(length = 2000)
    private String body;

    @Column(length = 200)
    private String image;

    /** Giỏ hàng tư vấn dược sĩ gửi kèm tin nhắn. */
    @ManyToOne(fetch = FetchType.LAZY)
    private SuggestedCart suggestedCart;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public boolean isAi() {
        return "AI".equals(kind);
    }

    public boolean isSystem() {
        return "SYSTEM".equals(kind);
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
