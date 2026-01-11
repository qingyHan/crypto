package com.crypto.api.websocket;

import com.crypto.common.entity.AlertRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * WebSocket 推送服务
 * <p>
 * 基于 STOMP 协议的实时消息广播服务。
 * </p>
 * 
 * <h3>订阅主题 (Topic):</h3>
 * <ul>
 *   <li>{@code /topic/alerts}: 所有预警广播</li>
 *   <li>{@code /topic/alerts/{symbol}}: 特定币种预警</li>
 *   <li>{@code /topic/market-stats}: 市场统计数据</li>
 *   <li>{@code /topic/price/{symbol}}: 实时价格更新</li>
 * </ul>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class WebSocketPushService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketPushService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * 推送预警消息到所有订阅的客户端
     * <p>
     * 客户端需要订阅 /topic/alerts 主题才能接收消息。
     * 消息会自动序列化为JSON格式发送。
     *
     * @param alertRecord 预警记录对象，不能为null
     */
    public void pushAlert(AlertRecord alertRecord) {
        if (alertRecord == null) {
            log.warn("尝试推送空的预警记录");
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/alerts", alertRecord);

            log.debug("推送预警到WebSocket：id={}，交易对={}，类型={}",
                    alertRecord.getId(), alertRecord.getSymbol(), alertRecord.getAlertType());

        } catch (Exception e) {
            log.error("通过WebSocket推送预警失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 推送预警消息到指定交易对的主题
     * <p>
     * 客户端需要订阅 /topic/alerts/{symbol} 主题才能接收特定交易对的预警消息。
     * 例如：订阅 /topic/alerts/BTCUSDT 可以只接收BTCUSDT的预警。
     *
     * @param symbol      交易对符号，如"BTCUSDT"，不能为空
     * @param alertRecord 预警记录对象，不能为null
     */
    public void pushAlertToSymbol(String symbol, AlertRecord alertRecord) {
        if (symbol == null || symbol.isBlank() || alertRecord == null) {
            log.warn("pushAlertToSymbol参数无效：交易对={}", symbol);
            return;
        }
        try {
            String destination = "/topic/alerts/" + symbol;
            messagingTemplate.convertAndSend(destination, alertRecord);

            log.debug("推送预警到WebSocket主题{}：id={}",
                    destination, alertRecord.getId());

        } catch (Exception e) {
            log.error("推送预警到交易对主题失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 推送市场统计数据
     * <p>
     * 客户端需要订阅 /topic/market-stats 主题
     *
     * @param statistics 市场统计数据
     */
    public void pushMarketStats(Map<String, Object> statistics) {
        if (statistics == null || statistics.isEmpty()) {
            log.warn("尝试推送空的统计数据");
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/market-stats", statistics);

            log.debug("推送市场统计数据到WebSocket");

        } catch (Exception e) {
            log.error("通过WebSocket推送市场统计数据失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 推送价格更新到指定交易对
     * <p>
     * 客户端需要订阅 /topic/price/{symbol} 主题
     *
     * @param symbol    交易对符号
     * @param priceData 价格数据
     */
    public void pushPriceUpdate(String symbol, Object priceData) {
        if (symbol == null || symbol.isBlank() || priceData == null) {
            return;
        }
        try {
            String destination = "/topic/price/" + symbol;
            messagingTemplate.convertAndSend(destination, priceData);

            log.trace("推送价格更新到WebSocket主题：{}", destination);

        } catch (Exception e) {
            log.error("推送价格更新失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 推送消息到指定用户
     * <p>
     * 客户端需要订阅 /user/queue/notifications 队列
     *
     * @param username 用户名
     * @param message  消息内容
     */
    public void pushToUser(String username, Object message) {
        if (username == null || username.isBlank() || message == null) {
            log.warn("pushToUser参数无效：用户名={}", username);
            return;
        }
        try {
            messagingTemplate.convertAndSendToUser(username, "/queue/notifications", message);

            log.debug("推送消息给用户：{}", username);

        } catch (Exception e) {
            log.error("推送消息给用户失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 广播系统消息
     * <p>
     * 客户端需要订阅 /topic/system 主题
     *
     * @param message 系统消息
     */
    public void broadcastSystemMessage(Object message) {
        if (message == null) {
            log.warn("尝试广播空的系统消息");
            return;
        }
        try {
            messagingTemplate.convertAndSend("/topic/system", message);

            log.info("广播系统消息");

        } catch (Exception e) {
            log.error("广播系统消息失败：{}", e.getMessage(), e);
        }
    }
}
