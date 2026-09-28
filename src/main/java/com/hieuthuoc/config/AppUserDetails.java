package com.hieuthuoc.config;

import com.hieuthuoc.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AppUserDetails implements UserDetails {
    private final Long id;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean locked;

    public AppUserDetails(User u) {
        this.id = u.getId();
        this.email = u.getEmail() != null ? u.getEmail() : u.getPhone();
        this.passwordHash = u.getPasswordHash();
        this.role = u.getRole().name();
        this.locked = u.isLocked();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !locked;
    }
}
