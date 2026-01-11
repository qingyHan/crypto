package com.crypto.api.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

/**
 * ClickHouse数据源配置
 * <p>
 * 用于查询ClickHouse中的K线数据和交易数据
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "clickhouse-config")
public class ClickHouseConfig {

    private String url = "jdbc:clickhouse://localhost:8123/crypto_db";
    private String username = "default";
    private String password = "";
    private String driverClassName = "com.clickhouse.jdbc.ClickHouseDriver";
    private PoolConfig pool = new PoolConfig();

    @Data
    public static class PoolConfig {
        private int maximumPoolSize = 10;
        private int minimumIdle = 2;
        private long connectionTimeout = 30000L;
        private long idleTimeout = 600000L;
        private long maxLifetime = 1800000L;
    }

    /**
     * 配置ClickHouse数据源
     * <p>
     * 使用HikariCP作为连接池，配置连接池参数和ClickHouse特定属性。
     * 连接池配置包括：最大连接数、最小空闲连接数、连接超时时间等。
     *
     * @return ClickHouse数据源实例
     */
    @Bean(name = "clickHouseDataSource")
    public DataSource clickHouseDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);

        // 连接池配置
        config.setMaximumPoolSize(pool.getMaximumPoolSize());
        config.setMinimumIdle(pool.getMinimumIdle());
        config.setConnectionTimeout(pool.getConnectionTimeout());
        config.setIdleTimeout(pool.getIdleTimeout());
        config.setMaxLifetime(pool.getMaxLifetime());

        // ClickHouse特定配置
        config.addDataSourceProperty("socket_timeout", "300000");
        config.addDataSourceProperty("dataTransferTimeout", "300000");

        // 设置连接池名称，便于监控和日志追踪
        config.setPoolName("ClickHouseHikariPool");
        log.info("ClickHouse数据源配置完成：{}", url);
        return new HikariDataSource(config);
    }

    /**
     * 配置ClickHouse JdbcTemplate
     * <p>
     * 创建JdbcTemplate实例用于执行ClickHouse查询操作。
     * 设置查询超时时间为5分钟（300秒），适用于大数据量查询场景。
     *
     * @return JdbcTemplate实例
     */
    @Bean(name = "clickHouseJdbcTemplate")
    public JdbcTemplate clickHouseJdbcTemplate() {
        DataSource dataSource = clickHouseDataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // 查询超时时间：5分钟（300秒）
        jdbcTemplate.setQueryTimeout(300);
        log.info("ClickHouse JdbcTemplate配置完成");
        return jdbcTemplate;
    }
}
