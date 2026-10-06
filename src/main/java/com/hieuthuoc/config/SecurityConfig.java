package com.hieuthuoc.config;

import com.hieuthuoc.entity.User;
import com.hieuthuoc.repository.UserRepository;
import com.hieuthuoc.service.AccountService;
import com.hieuthuoc.service.Cart;
import com.hieuthuoc.service.NotificationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.SessionFlashMapManager;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class SecurityConfig {
    /** Đường dẫn chỉ dành cho khách hàng (middleware role:CUSTOMER). */
    private static final String[] CUSTOMER_ONLY = {"/checkout", "/wishlist/**", "/consult", "/consult/**", "/account", "/account/**"};

    @Value("${app.remember-key:hieuthuoc-remember-me}")
    private String rememberKey;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // đọc được hash $2y$ của Laravel
    }

    /** Đăng nhập bằng email hoặc số điện thoại. */
    @Bean
    public UserDetailsService userDetailsService(ObjectProvider<AccountService> accounts) {
        return login -> {
            User u = accounts.getObject().findByLogin(login == null ? "" : login.trim());
            if (u == null) throw new UsernameNotFoundException("Không tìm thấy tài khoản");
            return new AppUserDetails(u);
        };
    }

    static void flash(HttpServletRequest req, HttpServletResponse res, Map<String, ?> values) {
        FlashMap fm = new FlashMap();
        fm.putAll(values);
        new SessionFlashMapManager().saveOutputFlashMap(fm, req, res);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, UserRepository userRepo, ObjectProvider<NotificationService> notifications,
                                           ObjectProvider<Cart> cart, UserDetailsService uds) throws Exception {
        HttpSessionCsrfTokenRepository csrfRepo = new HttpSessionCsrfTokenRepository();
        csrfRepo.setParameterName("_token"); // giống @csrf của Laravel (app.js cũng gửi _token)
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setMatchingRequestParameterName(null);

        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/staff/**").hasAnyRole("PHARMACIST", "ADMIN")
                        .requestMatchers(CUSTOMER_ONLY).hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/products/*/reviews").hasRole("CUSTOMER")
                        .requestMatchers("/notifications/**", "/files/**", "/payment/sandbox/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/cart/points").authenticated()
                        .anyRequest().permitAll())
                .csrf(c -> c.csrfTokenRepository(csrfRepo).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Cổng thanh toán / GHN gọi webhook từ máy chủ của họ (không có CSRF token); REST API dùng token riêng
                        .ignoringRequestMatchers("/payment/payos/webhook", "/payment/vnpay/ipn", "/shipping/ghn/webhook", "/api/**"))
                .requestCache(rc -> rc.requestCache(requestCache))
                .formLogin(f -> f
                        .loginPage("/login")
                        .loginProcessingUrl("/login")
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler((req, res, auth) -> {
                            if (auth.getPrincipal() instanceof AppUserDetails d) {
                                userRepo.findById(d.getId()).ifPresent(u -> {
                                    notifications.getObject().log(u, "auth.login", req.getRemoteAddr());
                                    SavedRequest saved = requestCache.getRequest(req, res);
                                    requestCache.removeRequest(req, res);
                                    try {
                                        res.sendRedirect(saved != null ? saved.getRedirectUrl() : req.getContextPath() + AccountService.homeFor(u));
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                            }
                        })
                        .failureHandler((req, res, ex) -> {
                            String username = req.getParameter("username");
                            String password = req.getParameter("password");
                            if (username == null || username.isBlank() || password == null || password.isEmpty()) {
                                List<String> errors = new java.util.ArrayList<>();
                                if (username == null || username.isBlank()) errors.add("Vui lòng nhập Email hoặc số điện thoại.");
                                if (password == null || password.isEmpty()) errors.add("Vui lòng nhập Mật khẩu.");
                                flash(req, res, Map.of("errors", errors, "old", Map.of("username", username == null ? "" : username)));
                            } else {
                                String msg = ex instanceof LockedException ? "Tài khoản đã bị khóa. Vui lòng liên hệ nhà thuốc."
                                        : "Email/số điện thoại hoặc mật khẩu không đúng.";
                                flash(req, res, Map.of("error", msg, "old", Map.of("username", username)));
                            }
                            res.sendRedirect(req.getContextPath() + "/login");
                        })
                        .permitAll())
                .rememberMe(r -> r.key(rememberKey).rememberMeParameter("remember").userDetailsService(uds).tokenValiditySeconds(60 * 60 * 24 * 30))
                .logout(l -> l.logoutUrl("/logout")
                        .addLogoutHandler((req, res, auth) -> {
                            try {
                                cart.getObject().clear();
                            } catch (RuntimeException ignored) {
                                // giỏ hàng gắn phiên - có thể không còn
                            }
                        })
                        .logoutSuccessHandler((req, res, auth) -> {
                            flash(req, res, Map.of("info", "Bạn đã đăng xuất."));
                            res.sendRedirect(req.getContextPath() + "/");
                        })
                        .permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> {
                            if (GlobalControllerAdvice.wantsJson(req)) {
                                res.setStatus(401);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter().write("{\"ok\":false,\"message\":\"Vui lòng đăng nhập.\"}");
                                return;
                            }
                            requestCache.saveRequest(req, res);
                            res.sendRedirect(req.getContextPath() + "/login");
                        })
                        .accessDeniedHandler((req, res, ex) -> {
                            String path = req.getRequestURI().substring(req.getContextPath().length());
                            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                            boolean staff = auth != null && auth.getAuthorities().stream()
                                    .anyMatch(g -> Set.of("ROLE_ADMIN", "ROLE_PHARMACIST").contains(g.getAuthority()));
                            boolean customerArea = path.startsWith("/account") || path.startsWith("/checkout") || path.startsWith("/consult")
                                    || path.startsWith("/wishlist") || path.matches("/products/[^/]+/reviews");
                            String msg = staff && customerArea
                                    ? "Chức năng này dành cho tài khoản khách hàng. Vui lòng đăng nhập bằng tài khoản khách hàng."
                                    : ex instanceof org.springframework.security.web.csrf.CsrfException ? null : "Bạn không có quyền truy cập trang này.";
                            int status = ex instanceof org.springframework.security.web.csrf.CsrfException ? 419 : 403;
                            if (GlobalControllerAdvice.wantsJson(req)) {
                                res.setStatus(status);
                                res.setContentType("application/json;charset=UTF-8");
                                res.getWriter().write("{\"ok\":false,\"message\":\"" + (msg == null ? "Phiên làm việc đã hết hạn." : msg) + "\"}");
                                return;
                            }
                            req.setAttribute("errorStatus", status);
                            req.setAttribute("errorMessage", msg);
                            res.setStatus(status);
                            req.getRequestDispatcher("/error-page").forward(req, res);
                        }))
                .addFilterAfter(lockedUserFilter(userRepo), BasicAuthenticationFilter.class);
        return http.build();
    }

    /** Tài khoản bị khóa trong khi đang đăng nhập sẽ bị đăng xuất ngay (TrackPresence của bản Laravel). */
    private OncePerRequestFilter lockedUserFilter(UserRepository userRepo) {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.getPrincipal() instanceof AppUserDetails d
                        && userRepo.findById(d.getId()).map(User::isLocked).orElse(true)) {
                    SecurityContextHolder.clearContext();
                    if (req.getSession(false) != null) req.getSession(false).invalidate();
                    flash(req, res, Map.of("error", "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ nhà thuốc."));
                    res.sendRedirect(req.getContextPath() + "/login");
                    return;
                }
                chain.doFilter(req, res);
            }

            @Override
            protected boolean shouldNotFilter(HttpServletRequest request) {
                String p = request.getRequestURI();
                return p.startsWith("/css/") || p.startsWith("/js/") || p.startsWith("/vendor/") || p.startsWith("/storage/") || p.startsWith("/favicon");
            }
        };
    }
}
