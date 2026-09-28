package com.hieuthuoc.service;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Giỏ bán hàng tại quầy của nhân viên (lưu trong session). Khóa "productId:unitId". */
@Component
@SessionScope
@Getter
@Setter
public class PosCart implements Serializable {
    private final Map<String, Integer> items = new LinkedHashMap<>();
    /** Khách thành viên tìm theo SĐT để cộng điểm (null = khách lẻ). */
    private Long customerId;

    public void clear() {
        items.clear();
        customerId = null;
    }
}
