package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {
    @Query("select a from ShiftAssignment a join fetch a.shift join fetch a.user where a.workDate between :from and :to order by a.workDate, a.shift.startTime")
    List<ShiftAssignment> findBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select a from ShiftAssignment a join fetch a.shift where a.user = :user and a.workDate between :from and :to order by a.workDate, a.shift.startTime")
    List<ShiftAssignment> findForUser(@Param("user") User user, @Param("from") LocalDate from, @Param("to") LocalDate to);

    boolean existsByUserAndWorkDateAndShift(User user, LocalDate workDate, WorkShift shift);

    long countByShift(WorkShift shift);
}
