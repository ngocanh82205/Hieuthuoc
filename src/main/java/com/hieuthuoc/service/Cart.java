package com.hieuthuoc.service;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.util.*;

/** Giỏ hàng lưu trong session: key "productId:unitId" => số lượng; khách tích chọn những dòng sẽ đặt. */
@Component
@SessionScope
public class Cart implements Serializable {
    private final LinkedHashMap<String, Integer> items = new LinkedHashMap<>();
    private final LinkedHashSet<String> unselected = new LinkedHashSet<>();
    private String voucher;
    private boolean usePoints;
    private String province;

    public static String key(long productId, Long unitId) {
        return productId + ":" + (unitId == null ? 0 : unitId);
    }

    public static long productIdOf(String key) {
        return Long.parseLong(key.split(":")[0]);
    }

    public static long unitIdOf(String key) {
        String[] a = key.split(":");
        return a.length > 1 ? Long.parseLong(a[1]) : 0;
    }

    public synchronized Map<String, Integer> items() {
        return new LinkedHashMap<>(items);
    }

    /** Các dòng được tích chọn để đặt hàng (mặc định: tất cả). */
    public synchronized Map<String, Integer> selectedItems() {
        Map<String, Integer> out = new LinkedHashMap<>(items);
        out.keySet().removeAll(unselected);
        return out;
    }

    public synchronized boolean isSelected(String key) {
        return !unselected.contains(key);
    }

    /** @param keys các dòng khách tích chọn; dòng còn lại bỏ chọn */
    public synchronized void select(Collection<String> keys) {
        unselected.clear();
        for (String k : items.keySet()) if (!keys.contains(k)) unselected.add(k);
    }

    public synchronized int count() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    public synchronized int quantityOf(String key) {
        return items.getOrDefault(key, 0);
    }

    public synchronized void add(long productId, Long unitId, int qty) {
        String k = key(productId, unitId);
        items.merge(k, qty, Integer::sum);
        unselected.remove(k); // vừa thêm thì tích chọn
    }

    public synchronized void set(String key, int qty) {
        if (qty <= 0) {
            items.remove(key);
            unselected.remove(key);
        } else {
            items.put(key, qty);
        }
    }

    public void remove(String key) {
        set(key, 0);
    }

    public synchronized String voucherCode() {
        return voucher;
    }

    public synchronized void setVoucher(String code) {
        this.voucher = code == null || code.isBlank() ? null : code.trim().toUpperCase();
    }

    public synchronized boolean usePoints() {
        return usePoints;
    }

    public synchronized void setUsePoints(boolean v) {
        this.usePoints = v;
    }

    public synchronized String province() {
        return province;
    }

    public synchronized void setProvince(String p) {
        this.province = p;
    }

    public synchronized void clear() {
        items.clear();
        unselected.clear();
        voucher = null;
        usePoints = false;
    }

    /** Sau khi đặt hàng: bỏ các dòng đã đặt, giữ lại các dòng khách chưa chọn. */
    public synchronized void removeSelected() {
        items.keySet().retainAll(unselected);
        unselected.clear();
        unselected.addAll(items.keySet());
        voucher = null;
        usePoints = false;
    }
}
