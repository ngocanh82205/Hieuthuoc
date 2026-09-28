package com.hieuthuoc.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Giỏ hàng lưu trong session. Khóa = "productId:unitId" (unitId = 0 là đơn vị gốc), giá trị = số lượng theo đơn vị đó.
 */
@Component
@SessionScope
@Getter
@Setter
public class Cart implements Serializable {
    private final Map<String, Integer> items = new LinkedHashMap<>();
    private String voucherCode;
    /** Khách chọn dùng điểm tích lũy để trừ tiền. */
    private boolean usePoints;
    /** Tỉnh/thành giao hàng đang chọn ở trang thanh toán (tính phí ship theo khu vực). */
    private String province;

    public static String key(Long productId, Long unitId) {
        return productId + ":" + (unitId == null ? 0 : unitId);
    }

    public static long productIdOf(String key) {
        return Long.parseLong(key.substring(0, key.indexOf(':')));
    }

    public static long unitIdOf(String key) {
        return Long.parseLong(key.substring(key.indexOf(':') + 1));
    }

    public int getCount() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int quantityOf(String key) {
        return items.getOrDefault(key, 0);
    }

    public void add(Long productId, Long unitId, int qty) {
        items.merge(key(productId, unitId), qty, Integer::sum);
    }

    public void clear() {
        items.clear();
        voucherCode = null;
        usePoints = false;
    }
}
