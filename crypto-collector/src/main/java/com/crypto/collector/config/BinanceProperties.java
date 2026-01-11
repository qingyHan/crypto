package com.crypto.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Binance WebSocket配置属性
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "binance.ws")
public class BinanceProperties {

    /**
     * WebSocket基础URL
     */
    private String baseUrl;

    /**
     * 需要订阅的交易对列表
     */
    private List<String> symbols;

    /**
     * 重连间隔(毫秒)
     */
    private long reconnectInterval = 5000;

    /**
     * 心跳间隔(毫秒)
     */
    private long pingInterval = 30000;
}
