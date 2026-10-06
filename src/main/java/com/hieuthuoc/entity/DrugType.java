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

    /** Phải có đơn thuốc: thuốc kê đơn và thuốc kiểm soát đặc biệt (gây nghiện, hướng thần, tiền chất). */
    public boolean isPrescription() {
        return this == ETC || this == SPECIAL;
    }

    /** Thuốc kiểm soát đặc biệt không bán online. */
    public boolean isSellableOnline() {
        return this != SPECIAL;
    }

    /** Chỉ OTC / TPCN / dụng cụ / mỹ phẩm được khuyến mãi. */
    public boolean isPromotable() {
        return this != ETC && this != SPECIAL;
    }

    public static DrugType tryFrom(String s) {
        if (s == null) return null;
        for (DrugType t : values()) if (t.name().equals(s)) return t;
        return null;
    }
}
