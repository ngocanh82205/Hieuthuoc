package com.hieuthuoc.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Giỏ hàng lưu trong session: productId -> số lượng. */
@Component
@SessionScope
@Getter
@Setter
public class Cart implements Serializable {
    private final Map<Long, Integer> items = new LinkedHashMap<>();
    private String voucherCode;

    public int getCount() {
        return items.values().stream().mapToInt(Integer::intValue).sum();
    }

    public int quantityOf(Long productId) {
        return items.getOrDefault(productId, 0);
    }

    public void clear() {
        items.clear();
        voucherCode = null;
    }
}
