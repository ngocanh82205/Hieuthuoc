package com.hieuthuoc.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Bộ nhớ đệm có thời hạn trong tiến trình (tương ứng Cache::remember của Laravel). Không lưu giá trị null. */
@Component
public class TtlCache {
    private record Entry(Object value, long expiresAt) {
    }

    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        Entry e = map.get(key);
        if (e == null) return null;
        if (e.expiresAt < System.currentTimeMillis()) {
            map.remove(key);
            return null;
        }
        return (T) e.value;
    }

    public void put(String key, Object value, long ttlSeconds) {
        if (value != null) map.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    public <T> T remember(String key, long ttlSeconds, Supplier<T> supplier) {
        T v = get(key);
        if (v != null) return v;
        v = supplier.get();
        put(key, v, ttlSeconds);
        return v;
    }

    public void forget(String key) {
        map.remove(key);
    }
}
