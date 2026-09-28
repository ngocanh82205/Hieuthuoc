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

public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {
    List<Prescription> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);

    List<Prescription> findTop200ByStatusOrderByReviewedAtDesc(ApprovalStatus status);

    List<Prescription> findTop5ByStatusOrderByCreatedAtAsc(ApprovalStatus status);

    long countByStatus(ApprovalStatus status);

    long countByPharmacist(User pharmacist);

    Optional<Prescription> findFirstByImage(String image);

    List<Prescription> findByUserAndStandaloneTrueOrderByCreatedAtDesc(User user);

    List<Prescription> findByStatusAndReviewedAtBetweenOrderByReviewedAtDesc(ApprovalStatus status, LocalDateTime from, LocalDateTime to);

    List<Prescription> findByReviewedAtBetween(LocalDateTime from, LocalDateTime to);
}
