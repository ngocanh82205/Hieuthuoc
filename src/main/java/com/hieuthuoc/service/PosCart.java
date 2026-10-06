package com.hieuthuoc.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Hóa đơn đang lập tại quầy (session): key "productId:unitId" => số lượng, khách thành viên (nếu có). */
@Component
@SessionScope
public class PosCart implements Serializable {
    private final LinkedHashMap<String, Integer> items = new LinkedHashMap<>();
    private Long customerId;

    public synchronized Map<String, Integer> items() {
        return new LinkedHashMap<>(items);
    }

    public synchronized void put(String key, int qty) {
        items.put(key, qty);
    }

    public synchronized boolean has(String key) {
        return items.containsKey(key);
    }

    public synchronized int get(String key) {
        return items.getOrDefault(key, 0);
    }

    public synchronized void remove(String key) {
        items.remove(key);
    }

    public synchronized Long customerId() {
        return customerId;
    }

    public synchronized void setCustomerId(Long id) {
        this.customerId = id;
    }

    public synchronized void clear() {
        items.clear();
        customerId = null;
    }
}
