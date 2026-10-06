package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaticPageRepository extends JpaRepository<StaticPage, Long> {
    List<StaticPage> findAllByOrderBySortOrderAscIdAsc();

    List<StaticPage> findByPublishedTrueAndShowInFooterTrueOrderBySortOrderAscIdAsc();

    Optional<StaticPage> findBySlugAndPublishedTrue(String slug);

    boolean existsBySlug(String slug);
}
