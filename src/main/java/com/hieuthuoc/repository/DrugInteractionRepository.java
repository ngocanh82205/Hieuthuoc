package com.hieuthuoc.repository;

import com.hieuthuoc.entity.DrugInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {
    List<DrugInteraction> findAllByOrderByIngredientAAscIngredientBAsc();
}
