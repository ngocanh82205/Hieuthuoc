package com.hieuthuoc.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import jakarta.persistence.TupleElement;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

/**
 * Truy vấn SQL thuần cho các phép tổng hợp (tương đương DB::table() của Laravel).
 * Chạy qua EntityManager nên dùng chung transaction và Hibernate tự flush thay đổi đang chờ trước khi đọc.
 */
@Component
public class Sql {
    @PersistenceContext
    private EntityManager em;

    public List<Map<String, Object>> rows(String sql, Map<String, ?> params) {
        Query q = em.createNativeQuery(sql, Tuple.class);
        bind(q, params);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : q.getResultList()) {
            Tuple t = (Tuple) o;
            Map<String, Object> m = new LinkedHashMap<>();
            for (TupleElement<?> e : t.getElements()) m.put(e.getAlias(), t.get(e));
            out.add(m);
        }
        return out;
    }

    public List<Map<String, Object>> rows(String sql) {
        return rows(sql, Map.of());
    }

    /** Hai cột (khóa, số) -> map. */
    public Map<Long, Long> longMap(String sql, Map<String, ?> params) {
        Map<Long, Long> out = new HashMap<>();
        for (Map<String, Object> r : rows(sql, params)) {
            Iterator<Object> it = r.values().iterator();
            out.put(toLong(it.next()), toLong(it.next()));
        }
        return out;
    }

    public long scalar(String sql, Map<String, ?> params) {
        Query q = em.createNativeQuery(sql);
        bind(q, params);
        List<?> list = q.getResultList();
        return list.isEmpty() ? 0 : toLong(list.get(0));
    }

    public int update(String sql, Map<String, ?> params) {
        Query q = em.createNativeQuery(sql);
        bind(q, params);
        return q.executeUpdate();
    }

    private static void bind(Query q, Map<String, ?> params) {
        if (params == null) return;
        for (Map.Entry<String, ?> e : params.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Enum<?> en) v = en.name();
            if (v instanceof Collection<?> c) {
                List<Object> list = new ArrayList<>();
                for (Object x : c) list.add(x instanceof Enum<?> en ? en.name() : x);
                v = list;
            }
            q.setParameter(e.getKey(), v);
        }
    }

    public static long toLong(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.longValue();
        if (o instanceof Boolean b) return b ? 1 : 0;
        return new BigDecimal(o.toString()).longValue();
    }

    public static double toDouble(Object o) {
        if (o == null) return 0;
        if (o instanceof Number n) return n.doubleValue();
        return Double.parseDouble(o.toString());
    }
}
