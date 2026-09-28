package com.hieuthuoc.repository;

import com.hieuthuoc.entity.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {
    List<Ingredient> findAllByOrderByNameAsc();

    Optional<Ingredient> findByNameIgnoreCase(String name);
}
