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

public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByPublishedTrueOrderByCreatedAtDesc();

    List<Post> findTop3ByPublishedTrueOrderByCreatedAtDesc();

    List<Post> findTop4ByPublishedTrueAndIdNotOrderByCreatedAtDesc(Long id);

    Optional<Post> findBySlugAndPublishedTrue(String slug);

    boolean existsBySlug(String slug);

    List<Post> findAllByOrderByCreatedAtDesc();
}
