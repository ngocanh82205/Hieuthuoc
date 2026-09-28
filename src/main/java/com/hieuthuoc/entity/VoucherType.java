package com.hieuthuoc.entity;

public enum VoucherType {
    PERCENT("Giảm theo %"),
    FIXED("Giảm số tiền cố định");

    private final String label;

    VoucherType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
