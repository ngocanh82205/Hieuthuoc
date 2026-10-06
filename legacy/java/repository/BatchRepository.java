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

public interface BatchRepository extends JpaRepository<Batch, Long> {
    List<Batch> findByProductOrderByExpDateAsc(Product product);

    Optional<Batch> findFirstByProductAndBatchNoIgnoreCase(Product product, String batchNo);

    @Query("select b from Batch b join fetch b.product p where b.quantity > 0 or b.locked = true order by p.name, b.expDate")
    List<Batch> findForStocktake();

    /** Lô hợp lệ để xuất kho theo FEFO: còn hàng, còn hạn, không bị khóa, hết hạn sớm nhất trước. */
    @Query("""
        select b from Batch b left join b.warehouse w where b.product.id = :productId and b.locked = false and b.quantity > 0
          and b.expDate >= :today and (w is null or w.sellable = true) order by b.expDate asc, b.id asc""")
    List<Batch> findSellableFefo(@Param("productId") Long productId, @Param("today") LocalDate today);

    @Query("""
        select b.product.id, sum(b.quantity) from Batch b left join b.warehouse w
        where b.locked = false and b.expDate >= :today and (w is null or w.sellable = true) group by b.product.id""")
    List<Object[]> sumOnHandByProduct(@Param("today") LocalDate today);

    @Query("select b from Batch b join fetch b.product where b.quantity > 0 and b.expDate < :today order by b.expDate")
    List<Batch> findExpired(@Param("today") LocalDate today);

    @Query("""
        select b from Batch b join fetch b.product where b.quantity > 0 and b.locked = false
          and b.expDate >= :today and b.expDate <= :limit order by b.expDate""")
    List<Batch> findNearExpiry(@Param("today") LocalDate today, @Param("limit") LocalDate limit);

    @Query("select b from Batch b join fetch b.product where b.locked = true and b.quantity > 0 order by b.expDate")
    List<Batch> findLocked();

    @Query("select coalesce(sum(b.quantity * b.importPrice), 0) from Batch b where b.quantity > 0 and b.expDate >= :from and b.expDate <= :to")
    long stockValueBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
