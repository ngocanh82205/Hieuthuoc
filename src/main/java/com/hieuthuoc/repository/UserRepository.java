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

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findByPhone(String phone);

    List<User> findByRoleAndLockedFalse(Role role);

    boolean existsByPhone(String phone);

    List<User> findByRoleInOrderByRoleAscFullNameAsc(Collection<Role> roles);

    List<User> findByRoleInAndLockedFalse(Collection<Role> roles);

    long countByRole(Role role);

    long countByRoleAndCreatedAtAfter(Role role, LocalDateTime after);

    @Query("""
        select u from User u where u.role = com.hieuthuoc.entity.Role.CUSTOMER and (:q = ''
          or lower(u.fullName) like lower(concat('%', :q, '%'))
          or lower(u.email) like lower(concat('%', :q, '%'))
          or u.phone like concat('%', :q, '%'))
        order by u.createdAt desc""")
    Page<User> searchCustomers(@Param("q") String q, Pageable pageable);
}
