package com.hieuthuoc.entity;

public enum ReturnStatus {
    REQUESTED("Đang yêu cầu đổi/trả", "warning"),
    APPROVED("Đã chấp nhận đổi/trả", "success"),
    REJECTED("Từ chối đổi/trả", "danger");

    private final String label;
    private final String color;

    ReturnStatus(String label, String color) {
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
