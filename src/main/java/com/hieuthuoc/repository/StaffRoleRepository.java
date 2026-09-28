package com.hieuthuoc.repository;

import com.hieuthuoc.entity.StaffRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRoleRepository extends JpaRepository<StaffRole, Long> {
    List<StaffRole> findAllByOrderByNameAsc();

    Optional<StaffRole> findByNameIgnoreCase(String name);
}
