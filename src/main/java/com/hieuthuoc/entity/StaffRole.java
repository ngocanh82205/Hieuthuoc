package com.hieuthuoc.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.*;

/** Vai trò nhân viên (RBAC): Dược sĩ quản lý, Dược sĩ, Dược sĩ phụ trách kho, Dược sĩ tư vấn... mỗi vai trò là một tập quyền do admin cấu hình. */
@Entity
@Table(name = "staff_roles")
@Getter
@Setter
@NoArgsConstructor
public class StaffRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(length = 300)
    private String permissions;

    public StaffRole(String name, String description, StaffPermission... perms) {
        this.name = name;
        this.description = description;
        setPermissionSet(Arrays.asList(perms));
    }

    public Set<String> getPermissionNames() {
        if (permissions == null || permissions.isBlank()) return Set.of();
        return new HashSet<>(Arrays.asList(permissions.split(",")));
    }

    public boolean has(StaffPermission p) {
        return getPermissionNames().contains(p.name());
    }

    public void setPermissionSet(Collection<StaffPermission> perms) {
        this.permissions = perms == null || perms.isEmpty() ? null
                : String.join(",", perms.stream().distinct().sorted().map(Enum::name).toList());
    }
}
