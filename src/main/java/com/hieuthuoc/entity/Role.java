package com.hieuthuoc.entity;

public enum Role {
    CUSTOMER("Khách hàng"),
    PHARMACIST("Dược sĩ / Nhân viên"),
    ADMIN("Quản trị viên");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
