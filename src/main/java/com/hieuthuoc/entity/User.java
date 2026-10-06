package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends Timestamped {
    /** Chi tiêu của 1 đơn: tiền hàng khách trả, không gồm phí giao hàng (khớp cách tính điểm tích lũy). */
    public static final String SPENT_SQL = "o.total - o.shippingFee";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role = Role.CUSTOMER;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    /** Email không bắt buộc - khách có thể đăng ký chỉ bằng số điện thoại. */
    @Column(unique = true, length = 150)
    private String email;

    @Column(unique = true, length = 20)
    private String phone;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    /** Bcrypt dạng $2y$ (dùng chung với Laravel). */
    @Column(nullable = false)
    private String password;

    @Column(name = "google_id", unique = true, length = 100)
    private String googleId;

    @Column(length = 300)
    private String avatar;

    @Column(length = 10)
    private String gender;

    private LocalDate birthday;

    /** Hồ sơ sức khỏe - chỉ dược sĩ xem khi tư vấn / duyệt đơn. */
    @Column(length = 500)
    private String allergies;

    @Column(name = "chronic_conditions", length = 500)
    private String chronicConditions;

    @Column(nullable = false)
    private boolean pregnancy;

    @Column(nullable = false)
    private int points;

    @Column(nullable = false)
    private boolean locked;

    /** Số chứng chỉ hành nghề dược (bắt buộc với dược sĩ duyệt đơn thuốc). */
    @Column(name = "license_no", length = 50)
    private String licenseNo;

    @Column(length = 200)
    private String degree;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "remember_token", length = 100)
    private String rememberToken;

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isCustomer() {
        return role == Role.CUSTOMER;
    }

    public boolean isStaff() {
        return role == Role.PHARMACIST || role == Role.ADMIN;
    }

    /** Admin luôn có toàn quyền; dược sĩ có bộ quyền cố định. */
    public boolean hasPermission(StaffPermission p) {
        return switch (role) {
            case ADMIN -> true;
            case PHARMACIST -> StaffPermission.FOR_PHARMACIST.contains(p);
            default -> false;
        };
    }

    /** Dùng trong template: ${user.can('CONTENT')} */
    public boolean can(String permission) {
        return hasPermission(StaffPermission.valueOf(permission));
    }

    public List<StaffPermission> permissionSet() {
        return Arrays.stream(StaffPermission.values()).filter(this::hasPermission).toList();
    }

    public String positionLabel() {
        return role == Role.ADMIN ? "Quản trị viên" : role.getLabel();
    }

    /** Online nếu có hoạt động trong 5 phút gần nhất. */
    public boolean isOnline() {
        return lastSeenAt != null && lastSeenAt.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    public String contact() {
        return phone != null && !phone.isEmpty() ? phone : (email != null ? email : "");
    }
}
