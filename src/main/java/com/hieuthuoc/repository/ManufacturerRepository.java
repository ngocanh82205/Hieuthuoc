package com.hieuthuoc.repository;

import com.hieuthuoc.entity.Manufacturer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ManufacturerRepository extends JpaRepository<Manufacturer, Long> {
    List<Manufacturer> findAllByOrderByNameAsc();

    Optional<Manufacturer> findByNameIgnoreCase(String name);
}
