package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/** Khu vực giao hàng & biểu phí ship theo tỉnh/thành. */
@Entity
@Table(name = "shipping_zones")
@Getter
@Setter
@NoArgsConstructor
public class ShippingZone {
    /** 34 tỉnh, thành phố (sau sắp xếp đơn vị hành chính 2025). */
    public static final List<String> PROVINCES = List.of(
            "Hà Nội", "TP. Hồ Chí Minh", "Hải Phòng", "Đà Nẵng", "Cần Thơ", "Huế",
            "Tuyên Quang", "Cao Bằng", "Lai Châu", "Lào Cai", "Thái Nguyên", "Điện Biên", "Lạng Sơn", "Sơn La", "Phú Thọ",
            "Bắc Ninh", "Quảng Ninh", "Hưng Yên", "Ninh Bình", "Thanh Hóa", "Nghệ An", "Hà Tĩnh", "Quảng Trị", "Quảng Ngãi",
            "Gia Lai", "Khánh Hòa", "Lâm Đồng", "Đắk Lắk", "Đồng Nai", "Tây Ninh", "Vĩnh Long", "Đồng Tháp", "Cà Mau", "An Giang");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    /** Danh sách tỉnh/thành, cách nhau dấu phẩy. */
    @Column(nullable = false, length = 1000)
    private String provinces;

    private long fee;

    /** Miễn phí ship cho đơn từ giá trị này (null = không miễn phí). */
    private Long freeThreshold;

    /** Thời gian giao dự kiến, VD: 1-2 ngày. */
    @Column(length = 50)
    private String eta;

    private boolean active = true;

    private int sortOrder;

    public ShippingZone(String name, List<String> provinces, long fee, Long freeThreshold, String eta, int sortOrder) {
        this.name = name;
        this.provinces = String.join(", ", provinces);
        this.fee = fee;
        this.freeThreshold = freeThreshold;
        this.eta = eta;
        this.sortOrder = sortOrder;
    }

    public List<String> getProvinceList() {
        return Arrays.stream(provinces.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public boolean covers(String province) {
        return province != null && getProvinceList().stream().anyMatch(p -> p.equalsIgnoreCase(province.trim()));
    }

    public long feeFor(long amount) {
        return freeThreshold != null && amount >= freeThreshold ? 0 : fee;
    }

    /** Đoán tỉnh/thành từ chuỗi địa chỉ (dùng gợi ý khi đặt hàng). */
    public static String detect(String address) {
        if (address == null) return null;
        String a = address.toLowerCase();
        if (a.contains("hcm") || a.contains("hồ chí minh") || a.contains("sài gòn")) return "TP. Hồ Chí Minh";
        for (String p : PROVINCES) if (a.contains(p.toLowerCase())) return p;
        return null;
    }
}
