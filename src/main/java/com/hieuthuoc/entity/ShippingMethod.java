package com.hieuthuoc.entity;

public enum ShippingMethod {
    DELIVERY("Giao hàng tận nơi"),
    PICKUP("Nhận tại nhà thuốc");

    private final String label;

    ShippingMethod(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
