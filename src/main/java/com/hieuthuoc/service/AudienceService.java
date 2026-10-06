package com.hieuthuoc.service;

import com.hieuthuoc.entity.MemberTier;
import com.hieuthuoc.entity.Role;
import com.hieuthuoc.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/** Chọn nhóm khách hàng cho email marketing / tặng voucher. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AudienceService {
    public static final Map<String, String> AUDIENCES = new LinkedHashMap<>();

    static {
        AUDIENCES.put("ALL", "Tất cả khách hàng");
        AUDIENCES.put("TIER", "Từ hạng thành viên trở lên");
        AUDIENCES.put("INACTIVE", "Khách lâu không mua");
        AUDIENCES.put("NEW", "Khách chưa từng mua hàng");
        AUDIENCES.put("PHONES", "Danh sách số điện thoại / email");
    }

    private final Sql sql;
    private final SettingService settings;

    @PersistenceContext
    private EntityManager em;

    public List<User> select(String audience, Form opts) {
        List<User> customers = em.createQuery("select u from User u where u.role = :r and u.locked = false order by u.id", User.class)
                .setParameter("r", Role.CUSTOMER).getResultList();
        switch (audience == null ? "" : audience) {
            case "ALL":
                return customers;
            case "TIER": {
                MemberTier tier = MemberTier.tryFrom(opts.get("tier"));
                if (tier == null) throw new BusinessException("Chọn hạng thành viên.");
                settings.all();
                Map<Long, Long> spent = sql.longMap("select user_id, sum(total - shipping_fee) as s from orders where status = 'COMPLETED' group by user_id", Map.of());
                return customers.stream().filter(u -> MemberTier.of(spent.getOrDefault(u.getId(), 0L)).ordinal() >= tier.ordinal()).toList();
            }
            case "INACTIVE": {
                int days = Math.max(1, opts.intVal("inactive_days", 60));
                Set<Long> active = new HashSet<>(em.createQuery("select distinct o.user.id from Order o where o.createdAt >= :t", Long.class)
                        .setParameter("t", LocalDateTime.now().minusDays(days)).getResultList());
                return customers.stream().filter(u -> !active.contains(u.getId())).toList();
            }
            case "NEW": {
                Set<Long> buyers = new HashSet<>(em.createQuery("select distinct o.user.id from Order o", Long.class).getResultList());
                return customers.stream().filter(u -> !buyers.contains(u.getId())).toList();
            }
            case "PHONES": {
                Set<String> set = new HashSet<>();
                for (String x : Objects.requireNonNullElse(opts.get("phones"), "").split("[,;\\s]+")) if (!x.trim().isEmpty()) set.add(x.trim().toLowerCase());
                return customers.stream().filter(u -> set.contains(Objects.requireNonNullElse(u.getPhone(), ""))
                        || set.contains(Objects.requireNonNullElse(u.getEmail(), "").toLowerCase())).toList();
            }
            default:
                throw new BusinessException("Chọn nhóm khách nhận.");
        }
    }
}
