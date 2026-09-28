package com.hieuthuoc.entity;

/** Phân loại sản phẩm - quyết định luồng bán hàng. */
public enum DrugType {
    OTC("Thuốc không kê đơn", "OTC", "success"),
    ETC("Thuốc kê đơn", "Thuốc kê đơn", "danger"),
    SPECIAL("Thuốc kiểm soát đặc biệt", "Không bán online", "dark"),
    SUPPLEMENT("Thực phẩm chức năng", "TPCN", "info"),
    DEVICE("Dụng cụ y tế", "Dụng cụ y tế", "secondary"),
    COSMETIC("Dược mỹ phẩm", "Dược mỹ phẩm", "primary");

    private final String label;
    private final String shortLabel;
    private final String color;

    DrugType(String label, String shortLabel, String color) {
        this.label = label;
        this.shortLabel = shortLabel;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public String getShortLabel() {
        return shortLabel;
    }

    public String getColor() {
        return color;
    }

    /** Thuốc kê đơn: phải có đơn thuốc được dược sĩ duyệt. */
    public boolean isPrescription() {
        return this == ETC;
    }

    /** Thuốc kiểm soát đặc biệt không được bán online. */
    public boolean isSellableOnline() {
        return this != SPECIAL;
    }
}
