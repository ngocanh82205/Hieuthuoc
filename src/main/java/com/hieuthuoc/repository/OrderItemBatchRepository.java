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

public interface OrderItemBatchRepository extends JpaRepository<OrderItemBatch, Long> {
    @Query("""
        select a from OrderItemBatch a join fetch a.orderItem oi join fetch oi.order o join fetch o.user
        where a.batch = :batch order by o.createdAt desc""")
    List<OrderItemBatch> findByBatchWithOrder(@Param("batch") Batch batch);

    @Query("select coalesce(sum(a.quantity), 0) from OrderItemBatch a where a.batch = :batch")
    long soldFromBatch(@Param("batch") Batch batch);
}
