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

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    Optional<Conversation> findFirstByCustomerOrderByIdDesc(User customer);

    List<Conversation> findByClosedOrderByUpdatedAtDesc(boolean closed);

    long countByClosedFalse();

    long countByPharmacistAndClosedFalse(User pharmacist);

    List<Conversation> findByClosedAndPharmacistOrderByUpdatedAtDesc(boolean closed, User pharmacist);

    List<Conversation> findByClosedAndPharmacistIsNullOrderByUpdatedAtDesc(boolean closed);

    long countByPharmacistAndUpdatedAtBetween(User pharmacist, java.time.LocalDateTime from, java.time.LocalDateTime to);
}
