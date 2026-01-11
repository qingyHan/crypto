package com.crypto.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * CORS跨域配置
 * 允许前端应用访问后端API
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class CorsConfig {

    /**
     * 配置CORS过滤器
     *
     * <p>
     * 允许所有域名、所有方法、所有请求头进行跨域访问
     *
     * @return CorsFilter实例
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // 允许凭证
        config.setAllowCredentials(true);

        // 允许所有域名（未做限制!）
        config.addAllowedOriginPattern("*");

        // 允许所有请求头
        config.addAllowedHeader("*");

        // 允许所有HTTP方法
        config.addAllowedMethod("*");

        // 暴露的响应头
        config.addExposedHeader("Authorization");
        config.addExposedHeader("Content-Disposition");

        // 预检请求的有效期(秒)
        config.setMaxAge(3600L);

        // 对所有路径应用CORS配置
        source.registerCorsConfiguration("/**", config);

        log.info("CORS过滤器配置完成（允许所有来源：*）");
        return new CorsFilter(source);
    }
}
