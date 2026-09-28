package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductQuestionRepository extends JpaRepository<ProductQuestion, Long> {
    List<ProductQuestion> findByProductAndHiddenFalseAndAnswerIsNotNullOrderByCreatedAtDesc(Product product);

    List<ProductQuestion> findByProductAndUserAndAnswerIsNullOrderByCreatedAtDesc(Product product, User user);

    List<ProductQuestion> findTop200ByOrderByCreatedAtDesc();

    List<ProductQuestion> findByAnswerIsNullAndHiddenFalseOrderByCreatedAtAsc();

    long countByAnswerIsNullAndHiddenFalse();
}
