package com.crypto.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 策略DTO
 * 用于策略配置的传输和展示
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 策略ID
     */
    private Long id;

    /**
     * 策略名称
     */
    private String strategyName;

    /**
     * 策略类型: PRICE_ALERT, VOLUME_ALERT, WHALE_ALERT, VOLATILITY_ALERT
     */
    private String strategyType;

    /**
     * 交易对符号(逗号分隔多个,*表示全部)
     */
    private String symbols;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    private Boolean enabled;

    /**
     * 参数配置(JSON格式)
     */
    private Map<String, Object> parameters;

    /**
     * 触发次数
     */
    private Long triggerCount;

    /**
     * 最后触发时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastTriggerTime;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 描述
     */
    private String description;

    /**
     * 价格预警策略参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceAlertParams implements Serializable {
        /**
         * 价格上涨阈值(百分比)
         */
        private BigDecimal spikeThreshold;

        /**
         * 价格下跌阈值(百分比)
         */
        private BigDecimal dropThreshold;

        /**
         * 时间窗口(分钟)
         */
        private Integer timeWindowMinutes;
    }

    /**
     * 交易量预警策略参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolumeAlertParams implements Serializable {
        /**
         * 交易量倍数阈值(相对于均值)
         */
        private BigDecimal volumeMultiplier;

        /**
         * 统计时间窗口(分钟)
         */
        private Integer statisticsWindowMinutes;
    }

    /**
     * 巨鲸交易预警策略参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WhaleAlertParams implements Serializable {
        /**
         * 单笔交易金额阈值(USDT)
         */
        private BigDecimal minTradeAmount;

        /**
         * 是否仅监控买单
         */
        private Boolean buyOnlyMonitor;
    }

    /**
     * 波动率预警策略参数
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VolatilityAlertParams implements Serializable {
        /**
         * 波动率阈值(百分比)
         */
        private BigDecimal volatilityThreshold;

        /**
         * 计算时间窗口(分钟)
         */
        private Integer calculationWindowMinutes;
    }

    /**
     * 验证策略配置
     *
     * @return true-有效, false-无效
     */
    public boolean isValid() {
        return strategyName != null && !strategyName.isEmpty()
                && strategyType != null && !strategyType.isEmpty()
                && symbols != null && !symbols.isEmpty()
                && parameters != null && !parameters.isEmpty();
    }
}
