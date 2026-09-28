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

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findByConversationAndIdGreaterThanOrderByIdAsc(Conversation conversation, Long afterId);

    Optional<Message> findFirstByConversationOrderByIdDesc(Conversation conversation);

    Optional<Message> findFirstByImage(String image);

    long countBySenderAndCreatedAtBetween(User sender, LocalDateTime from, LocalDateTime to);
}
