package com.crypto.api.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket STOMP配置。
 *
 * <p>
 * 用于实时预警推送。
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * 配置消息代理
     * 
     * <ul>
     * <li>/topic: 用于广播消息(一对多)
     * <li>/queue: 用于点对点消息(一对一)
     * <li>/app: 客户端发送消息的目标前缀
     * </ul>
     *
     * @param registry 消息代理注册器
     */
    @Override
    public void configureMessageBroker(@NonNull MessageBrokerRegistry registry) {
        // 启用简单消息代理,用于处理 /topic 和 /queue 前缀的消息
        registry.enableSimpleBroker("/topic", "/queue");

        // 设置应用程序目标前缀,客户端发送消息时使用
        registry.setApplicationDestinationPrefixes("/app");

        // 设置用户目标前缀,用于点对点消息
        registry.setUserDestinationPrefix("/user");

        log.info("WebSocket消息代理配置完成：/topic、/queue、/app");
    }

    /**
     * 注册STOMP端点
     * <p>
     * 客户端通过此端点连接WebSocket服务器
     * 支持SockJS回退选项以兼容不支持WebSocket的浏览器
     *
     * @param registry STOMP端点注册器
     */
    @Override
    public void registerStompEndpoints(@NonNull StompEndpointRegistry registry) {
        // 注册 /ws 端点,允许跨域,支持SockJS
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();

        log.info("STOMP端点注册完成：/ws（支持SockJS）");
    }
}
