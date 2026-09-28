package com.hieuthuoc.config;

import com.hieuthuoc.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final FileStorageService files;
    private final PresenceInterceptor presence;
    private final StaffAccessInterceptor staffAccess;

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(presence).addPathPatterns("/staff/**", "/admin/**", "/notifications/**");
        registry.addInterceptor(staffAccess).addPathPatterns("/staff/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ảnh sản phẩm là công khai; ảnh đơn thuốc/chat KHÔNG được phục vụ tĩnh.
        registry.addResourceHandler("/media/products/**")
                .addResourceLocations(files.dir(FileStorageService.Kind.PRODUCTS).toUri().toString());
        registry.addResourceHandler("/media/banners/**")
                .addResourceLocations(files.dir(FileStorageService.Kind.BANNERS).toUri().toString());
    }
}
