package com.hieuthuoc.config;

import com.hieuthuoc.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final FileStorageService files;
    private final PresenceInterceptor presence;
    private final StaffAccessInterceptor staffAccess;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(presence).excludePathPatterns("/css/**", "/js/**", "/vendor/**", "/storage/**", "/favicon.svg");
        registry.addInterceptor(staffAccess).addPathPatterns("/staff/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ảnh sản phẩm, bài viết là công khai (giống storage:link của Laravel); ảnh đơn thuốc / chat KHÔNG được phục vụ tĩnh.
        registry.addResourceHandler("/storage/**").addResourceLocations(files.publicRoot().toUri().toString());
    }
}
