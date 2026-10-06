package com.hieuthuoc.service;

import com.hieuthuoc.entity.MemberTier;
import com.hieuthuoc.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/** Chi tiêu và hạng thành viên của khách (tương ứng User::totalSpent() / tier() bên Laravel). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {
    private final Sql sql;
    private final SettingService settings;

    /** Tổng chi tiêu từ đơn đã hoàn thành (tiền hàng khách trả, không gồm phí giao hàng). */
    public long totalSpent(User u) {
        if (u == null || u.getId() == null) return 0;
        return sql.scalar("select coalesce(sum(total - shipping_fee), 0) from orders where user_id = :u and status = 'COMPLETED'", Map.of("u", u.getId()));
    }

    public MemberTier tier(User u) {
        settings.all();
        return MemberTier.of(totalSpent(u));
    }
}
