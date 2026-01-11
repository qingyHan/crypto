package com.crypto.collector.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OKX API 配置属性
 * 用于管理 OKX 交易所 API 的配置信息
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "okx.api")
public class OkxApiProperties {

    /**
     * OKX WebSocket URL
     */
    private String wsUrl = "wss://ws.okx.com:8443/ws/v5/public";

    /**
     * OKX API 基础 URL（生产环境）
     */
    private String baseUrl = "https://www.okx.com";

    /**
     * OKX API 测试 URL（沙箱环境）
     */
    private String testBaseUrl = "https://testnet.okx.com";

    /**
     * API 密钥
     */
    private String key;

    /**
     * API 密钥对应的密钥（Secret）
     */
    private String secret;

    /**
     * API 密钥对应的密码（Passphrase）
     */
    private String passphrase;

    /**
     * 运行环境：sandbox（沙箱）或 production（生产）
     */
    private String environment = "sandbox";

    /**
     * 连接超时时间（秒）
     */
    private int connectionTimeout = 30;

    /**
     * 重连间隔时间（秒）
     */
    private int reconnectInterval = 5;

    /**
     * 读取超时时间（毫秒）
     */
    private int readTimeout = 30000;

    /**
     * 写入超时时间（毫秒）
     */
    private int writeTimeout = 30000;

    /**
     * 支持的交易对列表
     */
    private java.util.List<String> symbols = java.util.Arrays.asList("BTC-USDT", "ETH-USDT");

    /**
     * API 速率限制（请求/分钟）
     */
    private int rateLimit = 10;

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 重试延迟（毫秒）
     */
    private int retryDelay = 1000;

    /**
     * 日志级别
     */
    private String logLevel = "INFO";

    /**
     * 获取有效的 API URL（基于环境配置）
     */
    public String getEffectiveBaseUrl() {
        return "sandbox".equalsIgnoreCase(environment) ? testBaseUrl : baseUrl;
    }

    /**
     * 获取交易对数组
     */
    public String[] getSymbolArray() {
        return symbols.toArray(new String[0]);
    }

    /**
     * 检查 API 凭证是否完整
     */
    public boolean isConfigured() {
        return key != null && !key.isEmpty() &&
               secret != null && !secret.isEmpty() &&
               passphrase != null && !passphrase.isEmpty();
    }

    /**
     * 初始化后处理：从环境变量OKX_SYMBOLS读取交易对配置
     * <p>
     * 环境变量格式：BTC-USDT,ETH-USDT,BNB-USDT,SOL-USDT
     */
    @PostConstruct
    public void initFromEnvironment() {
        String envSymbols = System.getenv("OKX_SYMBOLS");
        if (envSymbols != null && !envSymbols.trim().isEmpty()) {
            java.util.List<String> envSymbolList = java.util.Arrays.stream(envSymbols.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
            if (!envSymbolList.isEmpty()) {
                this.symbols = envSymbolList;
            }
        }
    }
}
