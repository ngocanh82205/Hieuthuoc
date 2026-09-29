package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    List<Payroll> findAllByOrderByMonthDesc();

    Optional<Payroll> findByMonth(String month);

    long countByStatus(String status);
}
