package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockSubscriptionRepository extends JpaRepository<StockSubscription, Long> {
    boolean existsByUserAndProductAndNotifiedFalse(User user, Product product);

    List<StockSubscription> findByProductAndNotifiedFalse(Product product);

    List<StockSubscription> findByUserAndNotifiedFalseOrderByCreatedAtDesc(User user);

    Optional<StockSubscription> findFirstByUserAndProductAndNotifiedFalse(User user, Product product);
}
