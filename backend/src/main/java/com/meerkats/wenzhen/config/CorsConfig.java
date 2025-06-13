package com.meerkats.wenzhen.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*") // 允许所有域名访问
                .allowedMethods("*") // 允许所有HTTP方法
                .allowCredentials(true) // 允许发送Cookie
                .allowedHeaders("*") // 允许所有头部信息
                .maxAge(3600); // 预检请求的有效期
    }
}
