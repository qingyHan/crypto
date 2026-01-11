package com.crypto.api.listener;

import com.crypto.api.websocket.WebSocketPushService;
import com.crypto.common.entity.AlertRecord;
import com.crypto.common.entity.KlineData;
import com.crypto.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis消息监听器
 * <p>
 * 监听Redis发布的消息并通过WebSocket推送给前端
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Component
public class RedisMessageListener implements MessageListener {

    private final RedisMessageListenerContainer listenerContainer;
    private final WebSocketPushService webSocketPushService;

    // Redis频道名称常量
    private static final String ALERT_CHANNEL = "crypto:alerts";
    private static final String KLINE_CHANNEL = "crypto:kline";
    private static final String PRICE_CHANNEL = "crypto:price";

    /**
     * 构造函数
     * @param listenerContainer 订阅Redis频道容器
     * @param webSocketPushService WebSocket推送服务
     */
    public RedisMessageListener(
            RedisMessageListenerContainer listenerContainer,
            WebSocketPushService webSocketPushService) {
        this.listenerContainer = listenerContainer;
        this.webSocketPushService = webSocketPushService;
    }

    /**
     * 初始化后订阅Redis频道
     */
    @PostConstruct
    public void init() {
        // 订阅预警频道
        listenerContainer.addMessageListener(this, new ChannelTopic(ALERT_CHANNEL));

        // 订阅K线数据频道
        listenerContainer.addMessageListener(this, new ChannelTopic(KLINE_CHANNEL));

        // 订阅价格更新频道
        listenerContainer.addMessageListener(this, new ChannelTopic(PRICE_CHANNEL));

        log.info("Redis消息监听器初始化完成，已订阅频道: {}, {}, {}",
                ALERT_CHANNEL, KLINE_CHANNEL, PRICE_CHANNEL);
    }

    /**
     * 接收Redis消息并转发到WebSocket
     */
    @Override
    public void onMessage(@NonNull Message message, @Nullable byte[] pattern) {
        try {
            String channel = new String(message.getChannel(), java.nio.charset.StandardCharsets.UTF_8);
            String body = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);

            log.debug("收到Redis消息，频道: {}", channel);

            switch (channel) {
                case ALERT_CHANNEL:
                    handleAlertMessage(body);
                    break;
                case KLINE_CHANNEL:
                    handleKlineMessage(body);
                    break;
                case PRICE_CHANNEL:
                    handlePriceMessage(body);
                    break;
                default:
                    log.warn("收到未知频道的消息: {}", channel);
            }

        } catch (Exception e) {
            log.error("处理Redis消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理预警消息
     */
    private void handleAlertMessage(String message) {
        try {
            AlertRecord alert = JsonUtil.fromJson(message, AlertRecord.class);
            if (alert != null) {
                // 推送到所有订阅预警的客户端
                webSocketPushService.pushAlert(alert);

                // 同时推送到该交易对的专属主题
                if (alert.getSymbol() != null) {
                    webSocketPushService.pushAlertToSymbol(alert.getSymbol(), alert);
                }

                log.debug("预警消息已转发到WebSocket: {}", alert.getSymbol());
            }
        } catch (Exception e) {
            log.error("处理预警消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理K线数据消息
     */
    private void handleKlineMessage(String message) {
        try {
            KlineData klineData = JsonUtil.fromJson(message, KlineData.class);
            if (klineData != null && klineData.getSymbol() != null) {
                // 推送到对应交易对的K线主题
                webSocketPushService.pushPriceUpdate(
                        klineData.getSymbol(),
                        klineData);

                log.trace("K线数据已转发到WebSocket: {}", klineData.getSymbol());
            }
        } catch (Exception e) {
            log.error("处理K线数据消息失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 处理价格更新消息
     */
    private void handlePriceMessage(String message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> priceData = JsonUtil.fromJson(message, HashMap.class);

            if (priceData != null && priceData.containsKey("symbol")) {
                String symbol = (String) priceData.get("symbol");

                // 推送到对应交易对的价格主题
                webSocketPushService.pushPriceUpdate(symbol, priceData);

                // 同时推送到市场统计主题
                webSocketPushService.pushMarketStats(priceData);

                log.trace("价格更新已转发到WebSocket: {}", symbol);
            }
        } catch (Exception e) {
            log.error("处理价格更新消息失败: {}", e.getMessage(), e);
        }
    }
}
