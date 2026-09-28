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

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Ảnh sản phẩm là công khai; ảnh đơn thuốc/chat KHÔNG được phục vụ tĩnh.
        registry.addResourceHandler("/media/products/**")
                .addResourceLocations(files.dir(FileStorageService.Kind.PRODUCTS).toUri().toString());
    }
}
