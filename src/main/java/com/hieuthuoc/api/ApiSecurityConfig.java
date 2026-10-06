package com.hieuthuoc.api;

import com.hieuthuoc.config.AppUserDetails;
import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Chuỗi bảo mật riêng cho REST API /api/**: phi trạng thái (không session, không CSRF),
 * xác thực bằng header "Authorization: Bearer &lt;token&gt;", lỗi trả JSON 401/403.
 */
@Configuration
public class ApiSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain apiFilterChain(HttpSecurity http, TokenService tokens, UserRepository users) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(c -> c.disable())
                .cors(c -> c.configurationSource(corsSource()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(rc -> rc.disable())
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register", "/api/v1/auth/forgot-password").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/categories/**", "/api/v1/products/**", "/api/v1/posts/**", "/api/v1/faqs",
                                "/api/v1/store").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/staff/**").hasAnyRole("PHARMACIST", "ADMIN")
                        .requestMatchers("/api/v1/cart/**", "/api/v1/orders/**", "/api/v1/me/**", "/api/v1/consult/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> Api.writeError(res, 401, "Chưa đăng nhập hoặc token không hợp lệ / đã hết hạn."))
                        .accessDeniedHandler((req, res, ex) -> Api.writeError(res, 403, "Tài khoản không có quyền truy cập chức năng này.")))
                .addFilterBefore(bearerFilter(tokens, users), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Đọc Bearer token, nạp người dùng vào SecurityContext (tài khoản bị khóa coi như chưa đăng nhập). */
    private OncePerRequestFilter bearerFilter(TokenService tokens, UserRepository users) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                String h = req.getHeader("Authorization");
                if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
                    Long id = tokens.verify(h.substring(7).trim());
                    User u = id == null ? null : users.findById(id).filter(x -> !x.isLocked()).orElse(null);
                    if (u != null) {
                        AppUserDetails d = new AppUserDetails(u);
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(d, null, d.getAuthorities()));
                    }
                }
                chain.doFilter(req, res);
            }
        };
    }

    /** Cho phép ứng dụng client (web SPA / mobile) gọi API từ origin khác. */
    private UrlBasedCorsConfigurationSource corsSource() {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOriginPatterns(List.of("*"));
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource s = new UrlBasedCorsConfigurationSource();
        s.registerCorsConfiguration("/api/**", c);
        return s;
    }
}
