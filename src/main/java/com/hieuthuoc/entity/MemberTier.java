package com.hieuthuoc.entity;

/**
 * Hạng thành viên theo tổng chi tiêu (đơn hoàn thành, không tính phí giao hàng). Hạng cao được nhân điểm tích lũy.
 * Ngưỡng và hệ số do admin cấu hình (settings: tier_*_min, tier_*_rate); SettingService nạp vào qua {@link #configure}.
 */
public enum MemberTier {
    DONG("Đồng", 0L, 100, "#b87333"),
    BAC("Bạc", 2_000_000L, 120, "#8e9aaf"),
    VANG("Vàng", 5_000_000L, 150, "#e0a800"),
    KIM_CUONG("Kim cương", 10_000_000L, 200, "#0b63e5");

    private final String label;
    private final long defaultMin;
    private final int defaultRate;
    private final String color;
    private volatile long minSpent;
    private volatile int rate;

    MemberTier(String label, long defaultMin, int defaultRate, String color) {
        this.label = label;
        this.defaultMin = defaultMin;
        this.defaultRate = defaultRate;
        this.color = color;
        this.minSpent = defaultMin;
        this.rate = defaultRate;
    }

    public String getLabel() {
        return label;
    }

    public String getColor() {
        return color;
    }

    public long getDefaultMin() {
        return defaultMin;
    }

    public int getDefaultRate() {
        return defaultRate;
    }

    public long getMinSpent() {
        return this == DONG ? 0 : minSpent;
    }

    /** Hệ số điểm (%). */
    public int getRate() {
        return rate;
    }

    public double getMultiplier() {
        return rate / 100.0;
    }

    public String getKey() {
        return name().toLowerCase();
    }

    public static void configure(MemberTier t, long minSpent, int rate) {
        t.minSpent = t == DONG ? 0 : minSpent;
        t.rate = rate;
    }

    public static MemberTier of(long spent) {
        MemberTier t = DONG;
        for (MemberTier m : values()) if (spent >= m.getMinSpent()) t = m;
        return t;
    }

    public MemberTier next() {
        return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
    }

    public static MemberTier tryFrom(String s) {
        if (s == null) return null;
        for (MemberTier t : values()) if (t.name().equals(s)) return t;
        return null;
    }
}
