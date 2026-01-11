package com.crypto.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI (Swagger) 配置
 * <p>
 * 配置OpenAPI文档，包括标题、版本、描述、联系人、许可证等
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * 配置OpenAPI文档
     * <p>
     * 设置API文档的基本信息，包括标题、版本、描述、联系方式和许可证，
     * 同时配置服务器地址，支持本地开发环境和生产环境
     *
     * @return OpenAPI配置对象
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("加密货币实时预警系统 API")
                        .version("1.0.0")
                        .description("""
                                # 功能概述

                                本系统提供以下核心API：

                                - **市场数据**: K线数据查询、实时价格
                                - **预警管理**: 预警规则配置、预警记录查询
                                - **策略回测**: MACD/RSI策略回测
                                - **数据流监控**: Kafka/Flink运行状态

                                ## 认证方式

                                所有非公开接口需要JWT Token认证
                                """)
                        .contact(new Contact()
                                .name("Crypto Analysis Team")
                                .email("crypto@example.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("本地开发环境"),
                        new Server()
                                .url("http://api.crypto.example.com")
                                .description("生产环境")));
    }
}
