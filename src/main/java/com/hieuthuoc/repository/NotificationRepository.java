package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findTop100ByUserOrderByIdDesc(User user);

    long countByUserAndSeenFalse(User user);

    @Modifying
    @Query("update Notification n set n.seen = true where n.user = :user and n.seen = false")
    int markAllSeen(@Param("user") User user);
}
