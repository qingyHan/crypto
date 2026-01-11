package com.crypto.api.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.lang.NonNull;

/**
 * Redis配置类
 * <p>
 * 配置RedisTemplate和序列化方式，支持JSON序列化和Java 8时间类型
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * 配置RedisTemplate
     * <p>
     * 序列化
     * <p>
     * Key序列化：String序列化，所有Key都以字符串形式存储
     * <p>
     * Value序列化：Jackson2JsonRedisSerializer，支持复杂Java对象的JSON序列化
     *
     * @param connectionFactory Redis连接工厂
     * @return RedisTemplate实例
     */
    @Bean
    @NonNull
    public RedisTemplate<String, Object> redisTemplate(@NonNull RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 配置ObjectMapper，支持Java 8时间类型
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        // 注意：不使用 activateDefaultTyping，因为 Collector 存储的是简单JSON
        // 这样可以兼容读取其他服务存储的数据

        // 注册JavaTimeModule以支持Java 8时间类型（LocalDateTime等）
        objectMapper.registerModule(new JavaTimeModule());
        // 禁用时间戳序列化，使用 ISO-8601 字符串（如：2025-01-01T12:00:00）
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 创建Jackson2JsonRedisSerializer用于Value序列化
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = 
                new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);

        // 创建String序列化器用于Key序列化
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();

        // 设置Key的序列化方式（String）
        template.setKeySerializer(stringRedisSerializer);
        template.setHashKeySerializer(stringRedisSerializer);

        // 设置Value的序列化方式（JSON）
        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();

        log.info("RedisTemplate配置完成（使用Jackson2JsonRedisSerializer）");
        return template;
    }

    /**
     * 配置Redis消息监听器容器
     * <p>
     * 用于订阅Redis发布的消息并实时推送到WebSocket。
     * <p>
     * 支持Redis的发布/订阅功能，实现消息的实时转发。
     *
     * @param connectionFactory Redis连接工厂
     * @return RedisMessageListenerContainer实例
     */
    @Bean
    @NonNull
    public RedisMessageListenerContainer redisMessageListenerContainer(
            @NonNull RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        log.info("RedisMessageListenerContainer配置完成（用于实时消息转发）");
        return container;
    }

}
