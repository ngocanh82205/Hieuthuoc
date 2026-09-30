package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollLineRepository extends JpaRepository<PayrollLine, Long> {
    @Query("select l from PayrollLine l join fetch l.payroll p where l.user = :user and p.status in ('APPROVED', 'PAID') order by p.month desc")
    List<PayrollLine> findPublishedForUser(@Param("user") User user);
}
