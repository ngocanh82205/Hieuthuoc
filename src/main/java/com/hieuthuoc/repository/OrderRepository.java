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

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByCodeAndUser(String code, User user);

    List<Order> findByUserOrderByCreatedAtDescIdDesc(User user);

    List<Order> findByUserAndStatusOrderByCreatedAtDescIdDesc(User user, OrderStatus status);

    long countByStatusIn(Collection<OrderStatus> statuses);

    long countByReturnStatus(ReturnStatus status);

    long countByUser(User user);

    boolean existsByCode(String code);

    @Query("select distinct oi.product from OrderItem oi where oi.order.user = :user and oi.order.createdAt >= :since and oi.order.status not in :excluded")
    List<Product> productsBoughtSince(@Param("user") User user, @Param("since") LocalDateTime since, @Param("excluded") Collection<OrderStatus> excluded);

    List<Order> findTop8ByStatusInOrderByCreatedAtAsc(Collection<OrderStatus> statuses);

    List<Order> findByStatusAndCompletedAtBetween(OrderStatus status, LocalDateTime from, LocalDateTime to);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    long countByStatusInAndCreatedAtBetween(Collection<OrderStatus> statuses, LocalDateTime from, LocalDateTime to);

    @Query("select o.status, count(o) from Order o group by o.status")
    List<Object[]> countGroupByStatus();

    @Query("select coalesce(sum(o.total), 0) from Order o where o.user = :user and o.status = com.hieuthuoc.entity.OrderStatus.COMPLETED")
    long totalSpent(@Param("user") User user);
}
