package com.hieuthuoc.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, length = 100)
    private String fullName;

    /** Email (không bắt buộc - khách có thể đăng ký chỉ bằng số điện thoại). */
    @Column(unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false)
    private String passwordHash;

    @Column(length = 10)
    private String gender;

    private LocalDate birthday;

    /** Số chứng chỉ hành nghề dược (với dược sĩ). */
    @Column(length = 50)
    private String licenseNo;

    /** Hồ sơ sức khỏe - chỉ dược sĩ được xem khi tư vấn/duyệt đơn. */
    @Column(length = 500)
    private String allergies;

    @Column(length = 500)
    private String chronicConditions;

    private boolean pregnancy;

    private int points;

    private boolean locked;

    /** Vai trò nhân viên (RBAC) - quyết định nhóm quyền chính. */
    @ManyToOne(fetch = FetchType.EAGER)
    private StaffRole staffRole;

    /** Quyền cấp thêm ngoài vai trò, cách nhau dấu phẩy (xem {@link StaffPermission}). */
    @Column(length = 200)
    private String permissions;

    /** Bằng cấp chuyên môn (VD: Dược sĩ đại học - ĐH Dược Hà Nội). */
    @Column(length = 200)
    private String degree;

    /** Ca làm việc (VD: Ca sáng 7h-15h). */
    @Column(length = 100)
    private String shift;

    /* ---- Lương (nhân viên) ---- */

    /** MONTHLY = lương tháng theo ngày công; HOURLY = lương theo giờ làm thực tế. */
    @Column(length = 10)
    private String salaryType;

    /** Lương cơ bản / tháng (MONTHLY). */
    private Long baseSalary;

    /** Đơn giá / giờ (HOURLY). */
    private Long hourlyRate;

    /** Phụ cấp cố định / tháng (trách nhiệm, xăng xe, ăn trưa...). */
    private Long allowance;

    public boolean isHourly() {
        return "HOURLY".equals(salaryType);
    }

    public String getSalaryLabel() {
        if (isHourly()) return hourlyRate == null ? "Chưa cấu hình" : String.format("%,d", hourlyRate).replace(',', '.') + " đ/giờ";
        return baseSalary == null ? "Chưa cấu hình" : String.format("%,d", baseSalary).replace(',', '.') + " đ/tháng";
    }

    /** Lần hoạt động gần nhất (để biết dược sĩ đang online). */
    private LocalDateTime lastSeenAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean hasPermission(StaffPermission p) {
        if (role == Role.ADMIN) return true;
        if (role != Role.PHARMACIST) return false;
        if (staffRole != null && staffRole.has(p)) return true;
        return permissions != null && java.util.Arrays.asList(permissions.split(",")).contains(p.name());
    }

    /** Dùng trong template: ${currentUser.can('CONTENT')} */
    public boolean can(String permission) {
        return hasPermission(StaffPermission.valueOf(permission));
    }

    public java.util.Set<StaffPermission> getPermissionSet() {
        java.util.Set<StaffPermission> set = java.util.EnumSet.noneOf(StaffPermission.class);
        for (StaffPermission p : StaffPermission.values()) if (hasPermission(p)) set.add(p);
        return set;
    }

    /** Quyền cấp thêm riêng cho người này (ngoài vai trò). */
    public boolean hasExtra(StaffPermission p) {
        return permissions != null && java.util.Arrays.asList(permissions.split(",")).contains(p.name());
    }

    public String getPositionLabel() {
        if (role == Role.ADMIN) return "Quản trị viên";
        return staffRole != null ? staffRole.getName() : role.getLabel();
    }

    /** Online nếu có hoạt động trong 5 phút gần nhất. */
    public boolean isOnline() {
        return lastSeenAt != null && lastSeenAt.isAfter(LocalDateTime.now().minusMinutes(5));
    }

    public boolean isStaff() {
        return role == Role.PHARMACIST || role == Role.ADMIN;
    }
}
