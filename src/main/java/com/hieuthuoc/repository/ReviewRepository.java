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

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByProductAndHiddenFalseOrderByCreatedAtDesc(Product product);

    boolean existsByProductAndUser(Product product, User user);

    List<Review> findTop200ByOrderByCreatedAtDesc();

    @Query("select r.product.id, avg(r.rating) from Review r where r.hidden = false group by r.product.id")
    List<Object[]> averageByProduct();
}
