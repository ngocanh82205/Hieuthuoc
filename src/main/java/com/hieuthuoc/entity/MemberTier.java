package com.hieuthuoc.entity;

/** Hạng thành viên theo tổng chi tiêu (đơn hoàn thành). Hạng cao được nhân điểm tích lũy. */
public enum MemberTier {
    DONG("Đồng", 0L, 1.0, "#b87333"),
    BAC("Bạc", 2_000_000L, 1.2, "#8e9aaf"),
    VANG("Vàng", 5_000_000L, 1.5, "#e0a800"),
    KIM_CUONG("Kim cương", 10_000_000L, 2.0, "#0b63e5");

    private final String label;
    private final long minSpent;
    private final double pointMultiplier;
    private final String color;

    MemberTier(String label, long minSpent, double pointMultiplier, String color) {
        this.label = label;
        this.minSpent = minSpent;
        this.pointMultiplier = pointMultiplier;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public long getMinSpent() {
        return minSpent;
    }

    public double getPointMultiplier() {
        return pointMultiplier;
    }

    public String getColor() {
        return color;
    }

    public static MemberTier of(long spent) {
        MemberTier t = DONG;
        for (MemberTier m : values()) if (spent >= m.minSpent) t = m;
        return t;
    }

    public MemberTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }
}
