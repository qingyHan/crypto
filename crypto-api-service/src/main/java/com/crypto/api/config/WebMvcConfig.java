package com.crypto.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Web MVC配置
 * 配置HTTP消息转换器，确保响应使用UTF-8编码
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /**
     * 配置HTTP消息转换器
     * 确保所有响应都使用UTF-8编码
     *
     * @param converters 消息转换器列表
     */
    @Override
    public void configureMessageConverters(@NonNull List<HttpMessageConverter<?>> converters) {
        // 配置String消息转换器，使用UTF-8编码
        StringHttpMessageConverter stringConverter = new StringHttpMessageConverter(StandardCharsets.UTF_8);
        stringConverter.setWriteAcceptCharset(false); // 不写入Accept-Charset头
        
        // 配置JSON消息转换器，确保使用UTF-8
        MappingJackson2HttpMessageConverter jsonConverter = new MappingJackson2HttpMessageConverter();
        jsonConverter.setDefaultCharset(StandardCharsets.UTF_8);
        
        // 先添加字符串转换器，再添加JSON转换器
        converters.add(0, stringConverter);
        converters.add(1, jsonConverter);
        
        log.info("HTTP消息转换器配置完成：使用UTF-8编码");
    }
}

