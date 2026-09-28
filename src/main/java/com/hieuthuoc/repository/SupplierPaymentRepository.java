package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SupplierPaymentRepository extends JpaRepository<SupplierPayment, Long> {
    List<SupplierPayment> findBySupplierOrderByPaidDateDescIdDesc(Supplier supplier);

    @Query("select coalesce(sum(p.amount), 0) from SupplierPayment p where p.supplier = :s")
    long totalPaid(@Param("s") Supplier s);
}
