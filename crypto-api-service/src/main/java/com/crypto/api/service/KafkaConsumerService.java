package com.crypto.api.service;

import com.alibaba.fastjson2.JSON;
import com.crypto.api.entity.StrategyConfig;
import com.crypto.api.websocket.WebSocketPushService;
import com.crypto.common.constants.KafkaTopics;
import com.crypto.common.entity.AlertRecord;
import com.crypto.common.enums.AlertType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Kafka 消费者服务
 * <p>
 * 系统的数据消费终端，负责将 Flink 产生的预警和统计数据落地并推送。
 * </p>
 * 
 * <h3>核心职责：</h3>
 * <ol>
 *   <li><strong>预警处理</strong>: 消费 {@code crypto-alerts} &rarr; 写入 MySQL &rarr; 匹配策略更新触发信息 &rarr; WebSocket 推送。</li>
 *   <li><strong>市场统计</strong>: 消费 {@code crypto-market-stats} &rarr; WebSocket 广播。</li>
 * </ol>
 * 
 * <h3>消费策略：</h3>
 * <ul>
 *   <li>消费者组: {@code crypto-api-service}</li>
 *   <li>并发数: 3 (默认配置)</li>
 *   <li>Offset提交: 自动提交 (Auto Commit)</li>
 * </ul>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class KafkaConsumerService {

    private final AlertService alertService;
    private final StrategyService strategyService;
    private final WebSocketPushService webSocketPushService;

    public KafkaConsumerService(AlertService alertService, 
                                 StrategyService strategyService,
                                 WebSocketPushService webSocketPushService) {
        this.alertService = alertService;
        this.strategyService = strategyService;
        this.webSocketPushService = webSocketPushService;
    }

    /**
     * 消费预警消息
     * <p>
     * 监听 {@link KafkaTopics#CRYPTO_ALERTS} 主题。
     * 收到消息后，先存入数据库，然后匹配策略更新触发信息，最后通过 WebSocket 推送给前端。
     * </p>
     * 
     * @param message 预警 JSON 字符串
     */
    @KafkaListener(topics = KafkaTopics.CRYPTO_ALERTS, groupId = "crypto-api-service", containerFactory = "kafkaListenerContainerFactory")
    public void consumeAlert(String message) {
        try {
            log.debug("收到预警消息：{}", message);

            // 解析预警消息为AlertRecord对象
            AlertRecord alertRecord = JSON.parseObject(message, AlertRecord.class);

            if (alertRecord == null) {
                log.warn("解析预警消息失败：{}", message);
                return;
            }

            // 保存预警记录到MySQL数据库
            boolean saved = alertService.saveAlert(alertRecord);

            if (saved) {
                log.info("保存预警到数据库：id={}，交易对={}，类型={}",
                        alertRecord.getId(), alertRecord.getSymbol(), alertRecord.getAlertType());

                // 匹配策略并更新触发信息（针对Flink生成的没有strategyId的预警）
                if (alertRecord.getStrategyId() == null) {
                    matchAndUpdateStrategy(alertRecord);
                }

                // 通过WebSocket推送给前端
                webSocketPushService.pushAlert(alertRecord);
                log.debug("推送预警到WebSocket客户端：id={}", alertRecord.getId());
            } else {
                log.error("保存预警失败：{}", message);
            }

        } catch (Exception e) {
            log.error("处理预警消息错误：{}", e.getMessage(), e);
        }
    }

    /**
     * 匹配预警对应的策略并更新触发信息
     * <p>
     * 根据预警的类型和交易对，查找匹配的已启用策略，并更新其触发次数和最后触发时间。
     * </p>
     *
     * @param alert 预警记录
     */
    private void matchAndUpdateStrategy(AlertRecord alert) {
        try {
            // 根据预警类型转换为策略类型
            String strategyType = mapAlertTypeToStrategyType(alert.getAlertType());
            if (strategyType == null) {
                return;
            }

            // 查询匹配该交易对的启用策略
            List<StrategyConfig> strategies = strategyService.getStrategiesBySymbol(alert.getSymbol());

            for (StrategyConfig strategy : strategies) {
                if (strategyType.equals(strategy.getStrategyType())) {
                    // 更新策略触发信息
                    strategyService.updateTriggerInfo(strategy.getId());
                    log.info("更新策略触发信息: strategyId={}, strategyName={}, alertType={}",
                            strategy.getId(), strategy.getStrategyName(), alert.getAlertType());
                    break; // 只匹配第一个符合条件的策略
                }
            }
        } catch (Exception e) {
            log.warn("匹配策略失败: symbol={}, alertType={}, error={}",
                    alert.getSymbol(), alert.getAlertType(), e.getMessage());
        }
    }

    /**
     * 将预警类型映射为策略类型
     *
     * @param alertType 预警类型
     * @return 策略类型字符串
     */
    private String mapAlertTypeToStrategyType(AlertType alertType) {
        if (alertType == null) {
            return null;
        }
        return switch (alertType) {
            case PRICE_SPIKE -> "PRICE_SPIKE";
            case PRICE_DROP -> "PRICE_DROP";
            case VOLUME_ANOMALY -> "VOLUME_ANOMALY";
            case WHALE_TRADE -> "WHALE_TRADE";
            default -> null; // 其他类型暂不匹配策略
        };
    }

    /**
     * 消费市场统计数据
     * <p>
     * 接收市场统计数据并通过WebSocket广播
     *
     * @param message 统计数据JSON
     */
    @KafkaListener(topics = KafkaTopics.CRYPTO_MARKET_STATS, groupId = "crypto-api-service", containerFactory = "kafkaListenerContainerFactory")
    public void consumeMarketStats(String message) {
        try {
            log.debug("收到市场统计数据消息：{}", message);

            // 解析统计数据
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> stats = JSON.parseObject(message, java.util.Map.class);

            if (stats != null && !stats.isEmpty()) {
                // 通过WebSocket广播市场统计
                webSocketPushService.pushMarketStats(stats);
                log.debug("推送市场统计数据到WebSocket客户端");
            }

        } catch (Exception e) {
            log.error("处理市场统计数据消息错误：{}", e.getMessage(), e);
        }
    }
}
