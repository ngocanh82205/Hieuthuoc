package com.hieuthuoc.entity;

/** Trạng thái duyệt dùng cho đơn thuốc và phiếu nhập kho. */
public enum ApprovalStatus {
    PENDING("Chờ duyệt", "warning"),
    APPROVED("Đã duyệt", "success"),
    REJECTED("Từ chối", "danger");

    private final String label;
    private final String color;

    ApprovalStatus(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }
}
