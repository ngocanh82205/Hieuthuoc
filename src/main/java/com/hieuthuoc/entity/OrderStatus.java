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
    PREPARING("Đang xử lý / soạn hàng", "primary"),
    PACKED("Đã đóng gói", "primary"),
    SHIPPING("Đang vận chuyển", "primary"),
    COMPLETED("Đã giao thành công", "success"),
    CANCELLED("Đã hủy", "secondary"),
    RETURNED("Đã trả hàng / hoàn tiền", "dark");

    /** Các trạng thái đang giữ chỗ tồn kho (chưa trừ thật). */
    public static final Set<OrderStatus> RESERVING = EnumSet.of(PENDING_RX, AWAITING_CUSTOMER, PENDING, CONFIRMED);

    /** Khách được hủy đến trước bước Đang vận chuyển. */
    public static final Set<OrderStatus> CUSTOMER_CANCELLABLE = EnumSet.of(PENDING_RX, RX_REJECTED, AWAITING_CUSTOMER, PENDING,
            CONFIRMED, PREPARING, PACKED);

    /** Hàng đã xuất kho (đã trừ tồn thật). */
    public static final Set<OrderStatus> ALLOCATED = EnumSet.of(PREPARING, PACKED, SHIPPING, COMPLETED);

    /** Các bước hiển thị trên thanh tiến trình của khách. */
    public static final List<OrderStatus> TIMELINE = List.of(PENDING, CONFIRMED, PREPARING, PACKED, SHIPPING, COMPLETED);

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

    /** Chuyển trạng thái nhân viên được phép (đơn đang giao KHÔNG được hủy). */
    public List<OrderStatus> staffTransitions() {
        return switch (this) {
            case AWAITING_CUSTOMER -> List.of(CANCELLED);
            case PENDING -> List.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> List.of(PREPARING, CANCELLED);
            case PREPARING -> List.of(PACKED, CANCELLED);
            case PACKED -> List.of(SHIPPING, CANCELLED);
            case SHIPPING -> List.of(COMPLETED, RETURNED); // RETURNED = giao thất bại, hàng hoàn về
            default -> List.of();
        };
    }

    public boolean isCustomerCancellable() {
        return CUSTOMER_CANCELLABLE.contains(this);
    }

    public boolean isAllocated() {
        return ALLOCATED.contains(this);
    }

    public boolean isReserving() {
        return RESERVING.contains(this);
    }
}
