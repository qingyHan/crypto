package com.crypto.api.websocket;

import com.crypto.api.service.AlertService;
import com.crypto.common.entity.AlertRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警WebSocket控制器。
 *
 * <p>
 * 处理客户端的WebSocket订阅和消息请求。
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Controller
public class AlertWebSocketController {

    private final AlertService alertService;

    public AlertWebSocketController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * 处理客户端订阅预警主题。
     *
     * <p>
     * 当客户端订阅 {@code /topic/alerts} 时，返回最近的预警列表。
     *
     * @return 最近的预警列表
     */
    @SubscribeMapping("/alerts")
    public List<AlertRecord> onSubscribeAlerts() {
        log.info("客户端订阅预警主题：/topic/alerts");

        // 返回最近10条预警
        return alertService.getRecentAlertsBySymbol("", 10);
    }

    /**
     * 处理客户端订阅指定交易对的预警主题。
     *
     * <p>
     * 当客户端订阅 {@code /topic/alerts/{symbol}} 时，返回该交易对的最近预警。
     *
     * @param symbol 交易对符号
     * @return 最近的预警列表
     */
    @SubscribeMapping("/alerts/{symbol}")
    public List<AlertRecord> onSubscribeSymbolAlerts(@DestinationVariable String symbol) {
        log.info("客户端订阅交易对预警主题：/topic/alerts/{}", symbol);

        // 返回该交易对最近10条预警
        return alertService.getRecentAlertsBySymbol(symbol, 10);
    }

    /**
     * 处理客户端请求未读预警数量。
     *
     * <p>
     * 客户端发送消息到 {@code /app/alerts/unread-count}，
     * 响应发送到 {@code /topic/alerts/unread-count}。
     *
     * @return 未读预警数量
     */
    @MessageMapping("/alerts/unread-count")
    @SendTo("/topic/alerts/unread-count")
    public Map<String, Object> getUnreadCount() {
        log.debug("客户端请求未读预警数量");

        Long count = alertService.countUnread();

        Map<String, Object> response = new HashMap<>();
        response.put("unreadCount", count);
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    /**
     * 处理客户端标记预警为已读。
     *
     * <p>
     * 客户端发送消息到 {@code /app/alerts/mark-read}。
     *
     * @param alertId 预警ID
     * @return 操作结果
     */
    @MessageMapping("/alerts/mark-read")
    @SendTo("/topic/alerts/read-status")
    public Map<String, Object> markAlertAsRead(Long alertId) {
        log.info("客户端标记预警为已读：id={}", alertId);

        boolean success = alertService.markAsRead(alertId);

        Map<String, Object> response = new HashMap<>();
        response.put("alertId", alertId);
        response.put("success", success);
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }

    /**
     * 处理客户端请求预警统计。
     *
     * <p>
     * 客户端发送消息到 {@code /app/alerts/statistics}，
     * 响应发送到 {@code /topic/alerts/statistics}。
     *
     * @return 预警统计信息
     */
    @MessageMapping("/alerts/statistics")
    @SendTo("/topic/alerts/statistics")
    public Map<String, Object> getAlertStatistics() {
        log.debug("客户端请求预警统计信息");

        java.time.LocalDateTime endTime = java.time.LocalDateTime.now();
        java.time.LocalDateTime startTime = endTime.minusHours(24);

        Map<String, Object> statistics = new HashMap<>();

        // 基本统计
        Map<String, Object> basicStats = alertService.getAlertStatistics(startTime, endTime);
        statistics.putAll(basicStats);

        // 按类型统计
        List<Map<String, Object>> typeStats = alertService.countByType(startTime, endTime);
        statistics.put("byType", typeStats);

        // 按严重程度统计
        List<Map<String, Object>> severityStats = alertService.countBySeverity(startTime, endTime);
        statistics.put("bySeverity", severityStats);

        // 未读数量
        Long unreadCount = alertService.countUnread();
        statistics.put("unreadCount", unreadCount);

        statistics.put("timestamp", System.currentTimeMillis());

        return statistics;
    }

    /**
     * 处理客户端心跳消息。
     *
     * <p>
     * 客户端发送消息到 {@code /app/heartbeat}，
     * 响应发送到 {@code /topic/heartbeat}。
     *
     * @return 心跳响应
     */
    @MessageMapping("/heartbeat")
    @SendTo("/topic/heartbeat")
    public Map<String, Object> heartbeat() {
        log.trace("收到客户端心跳消息");

        Map<String, Object> response = new HashMap<>();
        response.put("status", "alive");
        response.put("timestamp", System.currentTimeMillis());

        return response;
    }
}
