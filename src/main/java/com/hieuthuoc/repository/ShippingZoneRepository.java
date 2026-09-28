package com.hieuthuoc.repository;

import com.hieuthuoc.entity.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, Long> {
    List<ShippingZone> findAllByOrderBySortOrderAscIdAsc();

    List<ShippingZone> findByActiveTrueOrderBySortOrderAscIdAsc();
}
