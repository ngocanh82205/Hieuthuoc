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

    long countByAssignedToIdAndStatusIn(Long userId, Collection<OrderStatus> statuses);

    List<Order> findByPaymentStatusOrderByUpdatedAtAsc(PaymentStatus status);

    List<Order> findTop50ByPaymentStatusOrderByRefundedAtDesc(PaymentStatus status);

    @Query("select count(o) from Order o where o.user = :user and o.status not in :excluded")
    long countValidByUser(@Param("user") User user, @Param("excluded") Collection<OrderStatus> excluded);

    @Query("select count(o) from Order o where o.user = :user and upper(o.voucherCode) = upper(:code) and o.status not in :excluded")
    long countVoucherUse(@Param("user") User user, @Param("code") String code, @Param("excluded") Collection<OrderStatus> excluded);

    long countByUserAndStatusAndCompletedAtBefore(User user, OrderStatus status, LocalDateTime before);

    @Query("select distinct o from Order o join fetch o.items where o.status = com.hieuthuoc.entity.OrderStatus.COMPLETED and o.completedAt >= :from and o.completedAt < :to order by o.completedAt")
    List<Order> findCompletedWithItems(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
