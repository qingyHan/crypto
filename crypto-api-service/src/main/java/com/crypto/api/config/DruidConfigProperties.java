package com.crypto.api.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Druid连接池配置属性
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.datasource.druid")
public class DruidConfigProperties {

    private int initialSize = 5;
    private int minIdle = 5;
    private int maxActive = 20;
    private long maxWait = 60000L;
    private long timeBetweenEvictionRunsMillis = 60000L;
    private long minEvictableIdleTimeMillis = 300000L;
    private String validationQuery = "SELECT 1"; // 验证连接是否有效
    private boolean testWhileIdle = true; // 连接空闲时进行验证
    private boolean testOnBorrow = false; // 获取连接时进行验证
    private boolean testOnReturn = false; // 归还连接时进行验证

    private StatViewServlet statViewServlet = new StatViewServlet();
    private Filter filter = new Filter();

    /**
     * 监控配置
     */
    @Data
    public static class StatViewServlet {
        private boolean enabled = true;  // 是否启用监控
        private String urlPattern = "/druid/*"; // 监控页面路径
        private String loginUsername = "admin"; // 监控用户名
        private String loginPassword = "admin123"; // 监控密码
    }

    /**
     * 过滤器配置
     */
    @Data
    public static class Filter {
        private Stat stat = new Stat();

        @Data
        public static class Stat {
            private boolean enabled = true; // 是否启用统计
            private boolean logSlowSql = true; // 是否记录慢SQL
            private long slowSqlMillis = 2000L; // 慢SQL时间阈值
        }
    }
}
