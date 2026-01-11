package com.crypto.collector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 数据采集服务启动类
 * <p>
 * 数据采集模块主入口。系统的数据采集模块（crypto-collector）的启动类，负责启动数据采集服务。
 * 
 * 主要功能：
 * - 服务启动：启动Spring Boot应用，初始化数据采集服务
 * - 自动初始化：通过@PostConstruct注解，自动初始化DataCollectorService
 * - WebSocket连接：自动建立与OKX交易所的WebSocket连接
 * - Kafka生产者：初始化Kafka生产者，准备发送数据到消息队列
 * 
 * 启动后的初始化流程（DataCollectorService.init()）：
 * 1. 读取配置的交易对列表（如：BTC-USDT, ETH-USDT）
 * 2. 为每个交易对创建OkxWebSocketClient
 * 3. 建立WebSocket连接并订阅交易频道
 * 4. 初始化Redis监控统计
 * 5. 启动监控线程，定期检查连接状态
 * 
 * 数据流向：OKX交易所 → WebSocket → TradeEvent → JSON序列化 → Kafka (crypto-trades主题)
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@EnableScheduling
@SpringBootApplication
public class CollectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollectorApplication.class, args);
        log.info("========================================");
        log.info("Crypto Collector Service Started Successfully!");
        log.info("========================================");
    }
}
