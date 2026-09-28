package com.hieuthuoc.config;

import com.hieuthuoc.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepo) {
        // Đăng nhập bằng email hoặc số điện thoại
        return login -> {
            String s = login.trim();
            java.util.Optional<com.hieuthuoc.entity.User> u;
            if (s.contains("@")) {
                u = userRepo.findByEmailIgnoreCase(s);
            } else {
                java.util.List<com.hieuthuoc.entity.User> list = userRepo.findByPhone(s);
                u = list.size() == 1 ? java.util.Optional.of(list.get(0)) : java.util.Optional.empty();
            }
            return u.map(AppUserDetails::new).orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản"));
        };
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Sau đăng nhập: admin -> /admin, dược sĩ -> /staff, khách -> trang đang xem dở (hoặc trang chủ). */
    private AuthenticationSuccessHandler successHandler() {
        SavedRequestAwareAuthenticationSuccessHandler customer = new SavedRequestAwareAuthenticationSuccessHandler();
        customer.setDefaultTargetUrl("/");
        return (req, res, auth) -> {
            if (hasRole(auth, "ROLE_ADMIN")) res.sendRedirect(req.getContextPath() + "/admin");
            else if (hasRole(auth, "ROLE_PHARMACIST")) res.sendRedirect(req.getContextPath() + "/staff");
            else customer.onAuthenticationSuccess(req, res, auth);
        };
    }

    private static boolean hasRole(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(role));
    }

    /** Tài khoản bị admin khóa trong khi đang đăng nhập sẽ bị đăng xuất ở request kế tiếp. */
    private OncePerRequestFilter lockedUserFilter(UserRepository userRepo) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof AppUserDetails d
                        && userRepo.findById(d.getId()).map(u -> u.isLocked()).orElse(true)) {
                    SecurityContextHolder.clearContext();
                    if (req.getSession(false) != null) req.getSession(false).invalidate();
                    res.sendRedirect(req.getContextPath() + "/login?locked");
                    return;
                }
                chain.doFilter(req, res);
            }

            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String p = request.getRequestURI();
                return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/webjars/") || p.startsWith("/media/") || p.equals("/favicon.ico");
            }
        };
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository userRepo, SecurityContextRepository contextRepo) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/staff/**").hasAnyRole("PHARMACIST", "ADMIN")
                        .requestMatchers("/account/**", "/checkout/**", "/consult/**", "/wishlist/**", "/stock-alert/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/products/*/questions").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/products/*/reviews").hasRole("CUSTOMER")
                        .requestMatchers("/files/**", "/notifications/**").authenticated()
                        .anyRequest().permitAll())
                .securityContext(c -> c.securityContextRepository(contextRepo))
                .formLogin(f -> f
                        .loginPage("/login")
                        .usernameParameter("username")
                        .successHandler(successHandler())
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(l -> l.logoutUrl("/logout").logoutSuccessUrl("/?logout").permitAll())
                .exceptionHandling(e -> e.accessDeniedPage("/403"))
                .addFilterAfter(lockedUserFilter(userRepo), BasicAuthenticationFilter.class);
        return http.build();
    }
}
