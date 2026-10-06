package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Cuộc tư vấn giữa khách và nhà thuốc: trợ lý AI tiếp nhận trước (mode AI), chuyển dược sĩ khi cần (mode PHARMACIST). */
@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation extends Timestamped {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pharmacist_id")
    private User pharmacist;

    @Column(nullable = false)
    private boolean closed;

    @Column(nullable = false, length = 10)
    private String mode = "AI";

    @Column(name = "ai_summary", length = 1500)
    private String aiSummary;

    @Column(name = "handoff_reason", length = 200)
    private String handoffReason;

    @Column(name = "handed_off_at")
    private LocalDateTime handedOffAt;

    @Column(name = "triage_asked", nullable = false)
    private boolean triageAsked;

    @Column(name = "ai_misses", nullable = false)
    private int aiMisses;

    @OneToMany(mappedBy = "conversation")
    @OrderBy("id ASC")
    private List<Message> messages = new ArrayList<>();

    public boolean isAiMode() {
        return "AI".equals(mode);
    }
}
