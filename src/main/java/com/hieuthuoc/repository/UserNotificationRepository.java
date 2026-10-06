package com.hieuthuoc.repository;

import com.hieuthuoc.entity.UserNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserNotificationRepository extends JpaRepository<UserNotification, Long> {
    long countByUserIdAndSeenFalse(Long userId);

    List<UserNotification> findTop5ByUserIdAndSeenFalseOrderByIdDesc(Long userId);

    @Modifying
    @Query("update UserNotification n set n.seen = true where n.user.id = :userId and n.seen = false")
    int markAllSeen(@Param("userId") Long userId);

    @Modifying
    @Query("update UserNotification n set n.seen = true where n.user.id = :userId and n.id in :ids")
    int markSeen(@Param("userId") Long userId, @Param("ids") List<Long> ids);
}
