package com.crypto.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * API服务启动类 - Spring Boot应用主入口。
 *
 * <p>
 * 系统的API服务模块的启动类，负责启动Spring Boot应用并初始化所有组件。
 *
 * <h3>主要功能</h3>
 * <ul>
 * <li>应用启动：启动Spring Boot应用，加载所有配置和Bean</li>
 * <li>组件扫描：扫描{@code crypto.api}和{@code crypto.common}包下的所有组件</li>
 * <li>定时任务：启用Spring的定时任务功能（{@code @EnableScheduling}）</li>
 * <li>依赖注入：自动装配数据库、Redis、Kafka等连接</li>
 * </ul>
 *
 * <h3>启动后的初始化流程</h3>
 * <ol>
 * <li>加载{@code application.yml}配置文件</li>
 * <li>初始化数据源（MySQL、ClickHouse、Redis）</li>
 * <li>创建Kafka消费者（{@code KafkaConsumerService}）</li>
 * <li>启动WebSocket服务器</li>
 * <li>启动定时任务（{@code StrategyMonitorService}每10秒执行一次）</li>
 * <li>启动内嵌Tomcat服务器，监听8080端口</li>
 * </ol>
 *
 * <h3>关键注解</h3>
 * <ul>
 * <li>{@code @SpringBootApplication}：Spring Boot主注解</li>
 * <li>{@code @EnableScheduling}：启用定时任务功能</li>
 * </ul>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@EnableScheduling
@SpringBootApplication(scanBasePackages = { "com.crypto.api", "com.crypto.common" })
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
        log.info("========================================");
        log.info("加密货币API服务启动成功！");
        log.info("========================================");
    }
}
