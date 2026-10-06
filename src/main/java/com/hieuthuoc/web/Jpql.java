package com.hieuthuoc.web;

import com.hieuthuoc.service.Page;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;

import java.util.*;

/** Ghép điều kiện lọc JPQL động (tương ứng ->when(...) của Eloquent) và phân trang. */
public class Jpql {
    private final String from;
    private final String alias;
    private final List<String> where = new ArrayList<>();
    private final Map<String, Object> params = new LinkedHashMap<>();

    /** from = "Order o", alias = "o". */
    public Jpql(String from, String alias) {
        this.from = from;
        this.alias = alias;
    }

    public Jpql where(String cond, Object... nameValues) {
        where.add(cond);
        for (int i = 0; i + 1 < nameValues.length; i += 2) params.put((String) nameValues[i], nameValues[i + 1]);
        return this;
    }

    public Jpql when(boolean test, String cond, Object... nameValues) {
        return test ? where(cond, nameValues) : this;
    }

    /** Tìm kiếm LIKE không phân biệt hoa thường trên nhiều cột. */
    public Jpql search(String q, String... columns) {
        if (q == null || q.isBlank()) return this;
        StringJoiner sj = new StringJoiner(" or ", "(", ")");
        for (String c : columns) sj.add("lower(" + c + ") like :q_");
        return where(sj.toString(), "q_", "%" + q.trim().toLowerCase() + "%");
    }

    private String whereSql() {
        return where.isEmpty() ? "" : " where " + String.join(" and ", where);
    }

    private <T> TypedQuery<T> bind(TypedQuery<T> q) {
        params.forEach(q::setParameter);
        return q;
    }

    public <T> List<T> list(EntityManager em, Class<T> cls, String orderBy, int limit) {
        TypedQuery<T> q = bind(em.createQuery("select " + alias + " from " + from + whereSql() + (orderBy != null ? " order by " + orderBy : ""), cls));
        if (limit > 0) q.setMaxResults(limit);
        return q.getResultList();
    }

    public long count(EntityManager em) {
        return bind(em.createQuery("select count(" + alias + ") from " + from + whereSql(), Long.class)).getSingleResult();
    }

    public <T> Page<T> page(EntityManager em, Class<T> cls, String orderBy, int perPage, int page) {
        long total = count(em);
        int last = Math.max(1, (int) Math.ceil(total / (double) perPage));
        int p = Math.min(Math.max(1, page), last);
        TypedQuery<T> q = bind(em.createQuery("select " + alias + " from " + from + whereSql() + (orderBy != null ? " order by " + orderBy : ""), cls));
        return new Page<>(q.setFirstResult((p - 1) * perPage).setMaxResults(perPage).getResultList(), total, perPage, p);
    }
}
