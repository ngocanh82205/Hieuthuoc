package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TransferSlipRepository extends JpaRepository<TransferSlip, Long> {
    List<TransferSlip> findAllByOrderByIdDesc();
}
