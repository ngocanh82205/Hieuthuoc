package com.hieuthuoc.entity;

public enum ReminderType {
    MEDICATION("Nhắc uống thuốc"),
    REPURCHASE("Nhắc mua lại");

    private final String label;

    ReminderType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
