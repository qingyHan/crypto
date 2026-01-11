package com.crypto.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate配置类
 * 
 * @author Qingyang Han
 * @since 1.0.0
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 默认RestTemplate（主Bean）
     */
    @Bean
    @Primary
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); // 5秒连接超时
        factory.setReadTimeout(10000); // 10秒读取超时
        return new RestTemplate(factory);
    }

    /**
     * Flink专用RestTemplate
     * 用于DataFlowMonitorController调用Flink REST API
     */
    @Bean("flinkRestTemplate")
    public RestTemplate flinkRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000); // 3秒连接超时（Flink API响应较快）
        factory.setReadTimeout(5000); // 5秒读取超时
        return new RestTemplate(factory);
    }
}
