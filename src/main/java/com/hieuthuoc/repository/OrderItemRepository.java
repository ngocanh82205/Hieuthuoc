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

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("select oi.product.id, sum(oi.quantity) from OrderItem oi where oi.order.status in :statuses group by oi.product.id")
    List<Object[]> sumQuantityByProduct(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("""
        select count(oi) > 0 from OrderItem oi where oi.order.user = :user and oi.product = :product
          and oi.order.status = com.hieuthuoc.entity.OrderStatus.COMPLETED""")
    boolean hasPurchased(@Param("user") User user, @Param("product") Product product);
}
