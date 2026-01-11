package com.crypto.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 安全配置属性
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "security-config")
public class SecurityConfigProperties {

    private JwtConfig jwt = new JwtConfig();

    /**
     * JWT配置
     */
    @Data
    public static class JwtConfig {
        private String secret = "crypto-analysis-platform-secret-key-2026";
        private long expiration = 86400000L;  // 24小时(毫秒)
    }
}
