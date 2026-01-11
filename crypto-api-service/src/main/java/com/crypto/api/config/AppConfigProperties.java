package com.crypto.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 应用自定义配置属性
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app-config")
public class AppConfigProperties {

    private PageConfig page = new PageConfig();
    private CacheConfig cache = new CacheConfig();
    private AlertConfig alert = new AlertConfig();

    /**
     * 分页配置
     */
    @Data
    public static class PageConfig {
        private int defaultSize = 20;
        private int maxSize = 100;
    }

    /**
     * 缓存配置
     */
    @Data
    public static class CacheConfig {
        private int priceTtl = 30;
        private int klineTtl = 60;
        private int statsTtl = 300;
    }

    /**
     * 预警配置
     */
    @Data
    public static class AlertConfig {
        private int expiredDays = 30;
        private int batchSize = 100;
    }
}
