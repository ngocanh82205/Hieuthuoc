package com.hieuthuoc.repository;

import com.hieuthuoc.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CallbackRequestRepository extends JpaRepository<CallbackRequest, Long> {
    List<CallbackRequest> findByDoneOrderByCreatedAtAsc(boolean done);

    List<CallbackRequest> findTop100ByDoneOrderByHandledAtDesc(boolean done);

    long countByDoneFalse();

    List<CallbackRequest> findTop5ByUserOrderByCreatedAtDesc(User user);
}
