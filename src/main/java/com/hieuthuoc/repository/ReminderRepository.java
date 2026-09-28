package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {
    List<Reminder> findByUserOrderByActiveDescCreatedAtDesc(User user);

    Optional<Reminder> findByIdAndUser(Long id, User user);

    @Query("select r from Reminder r join fetch r.user where r.active = true")
    List<Reminder> findAllActive();
}
