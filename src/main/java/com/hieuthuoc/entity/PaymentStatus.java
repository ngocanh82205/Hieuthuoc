package com.hieuthuoc.entity;

public enum PaymentStatus {
    UNPAID("Chưa thanh toán", "warning"),
    PAID("Đã thanh toán", "success"),
    REFUNDED("Đã hoàn tiền", "secondary");

    private final String label;
    private final String color;

    PaymentStatus(String label, String color) {
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
