package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private User customer;

    /** Dược sĩ nhận tư vấn (gán khi dược sĩ trả lời lần đầu). */
    @ManyToOne(fetch = FetchType.LAZY)
    private User pharmacist;

    private boolean closed;

    /** AI = trợ lý AI đang hỗ trợ; HUMAN (hoặc null) = dược sĩ / nhân viên tư vấn. */
    @Column(length = 10)
    private String mode;

    /** Tóm tắt của trợ lý AI khi chuyển cho dược sĩ, và lý do chuyển. */
    @Column(length = 1500)
    private String aiSummary;

    @Column(length = 200)
    private String handoffReason;

    private LocalDateTime handedOffAt;

    /** Trợ lý đã hỏi sàng lọc triệu chứng (chế độ trả lời tự động). */
    private Boolean triageAsked;

    /** Số lần trợ lý không hiểu câu hỏi liên tiếp. */
    private Integer aiMisses;

    public boolean isAiMode() {
        return "AI".equals(mode);
    }

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = createdAt;
    }
}
