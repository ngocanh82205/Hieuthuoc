package com.hieuthuoc.entity;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public enum OrderStatus {
    PENDING_RX("Chờ dược sĩ duyệt đơn thuốc", "warning"),
    RX_REJECTED("Đơn thuốc bị từ chối", "danger"),
    AWAITING_CUSTOMER("Chờ khách xác nhận", "warning"),
    PENDING("Chờ xác nhận", "info"),
    CONFIRMED("Đã xác nhận", "primary"),
    PREPARING("Đang chuẩn bị hàng", "primary"),
    SHIPPING("Đang giao hàng", "primary"),
    COMPLETED("Hoàn thành", "success"),
    CANCELLED("Đã hủy", "secondary"),
    RETURNED("Đã trả hàng / hoàn tiền", "dark");

    /** Trạng thái đang giữ chỗ tồn kho (chưa xuất kho theo lô). */
    public static final Set<OrderStatus> RESERVING = EnumSet.of(PENDING_RX, AWAITING_CUSTOMER, PENDING, CONFIRMED);

    /** Khách được tự hủy khi đơn chưa chuyển sang Đang giao. */
    public static final Set<OrderStatus> CUSTOMER_CANCELLABLE = EnumSet.of(PENDING_RX, RX_REJECTED, AWAITING_CUSTOMER, PENDING, CONFIRMED, PREPARING);

    private final String label;
    private final String color;

    OrderStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    /** Các bước chuyển trạng thái mà dược sĩ/nhân viên được phép thực hiện. */
    public List<OrderStatus> staffTransitions() {
        return switch (this) {
            case AWAITING_CUSTOMER -> List.of(CANCELLED);
            case PENDING -> List.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> List.of(PREPARING, CANCELLED);
            case PREPARING -> List.of(SHIPPING, CANCELLED);
            case SHIPPING -> List.of(COMPLETED, CANCELLED);
            default -> List.of();
        };
    }

    public boolean isCustomerCancellable() {
        return CUSTOMER_CANCELLABLE.contains(this);
    }
}
