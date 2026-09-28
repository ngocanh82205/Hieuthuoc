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

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Optional<Product> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);

    List<Product> findAllByOrderByNameAsc();

    List<Product> findByActiveTrueOrderByNameAsc();

    long countByCategory(Category category);

    List<Product> findTop4ByActiveTrueAndActiveIngredientIgnoreCaseAndIdNot(String activeIngredient, Long id);

    List<Product> findTop4ByActiveTrueAndCategoryAndIdNotAndDrugTypeNot(Category category, Long id, DrugType drugType);
}
