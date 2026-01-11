package com.crypto.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka主题配置属性
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "kafka-topics")
public class KafkaTopicsProperties {

    private String trades = "crypto-trades";
    private String kline = "crypto-kline";
    private String alerts = "crypto-alerts";
}
