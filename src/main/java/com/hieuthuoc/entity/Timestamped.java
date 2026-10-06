package com.hieuthuoc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** Cột created_at / updated_at như Eloquent: tự điền khi thêm, cập nhật updated_at khi sửa. */
@MappedSuperclass
@Getter
@Setter
public abstract class Timestamped {
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now().withNano(0);
    }

    /** Đánh dấu bản ghi vừa được cập nhật (giống $model->touch()). */
    public void touch() {
        updatedAt = LocalDateTime.now().withNano(0);
    }
}
