package com.crypto.common.enums;

import lombok.Getter;

/**
 * 预警严重程度枚举
 * <p>
 * 定义预警的紧急程度，用于前端区分展示样式和通知优先级。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Getter
public enum AlertSeverity {

    /**
     * 低
     */
    LOW("LOW", "低", 1),

    /**
     * 中
     */
    MEDIUM("MEDIUM", "中", 2),

    /**
     * 高
     */
    HIGH("HIGH", "高", 3),

    /**
     * 严重
     */
    CRITICAL("CRITICAL", "严重", 4);

    private final String code;
    private final String description;
    private final int level;

    AlertSeverity(String code, String description, int level) {
        this.code = code;
        this.description = description;
        this.level = level;
    }

    /**
     * 根据code获取枚举
     */
    public static AlertSeverity fromCode(String code) {
        for (AlertSeverity severity : values()) {
            if (severity.getCode().equals(code)) {
                return severity;
            }
        }
        throw new IllegalArgumentException("Unknown AlertSeverity code: " + code);
    }

    /**
     * 根据变化百分比判断严重程度
     * 
     * @param changePercent 变化百分比(绝对值)
     * @return 严重程度
     */
    public static AlertSeverity fromChangePercent(double changePercent) {
        double absChange = Math.abs(changePercent);
        if (absChange >= 10.0) {
            return CRITICAL;
        } else if (absChange >= 5.0) {
            return HIGH;
        } else if (absChange >= 3.0) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }

    /**
     * 根据巨鲸交易金额判断严重程度
     * 
     * @param tradeAmountUsd 交易金额
     * @return 严重程度
     */
    public static AlertSeverity fromWhaleTradeAmount(double tradeAmountUsd) {
        if (tradeAmountUsd >= 10_000_000) { // 1000万美元以上
            return CRITICAL;
        } else if (tradeAmountUsd >= 5_000_000) { // 500万美元以上
            return HIGH;
        } else if (tradeAmountUsd >= 2_000_000) { // 200万美元以上
            return MEDIUM;
        } else {
            return LOW;
        }
    }

    /**
     * 根据交易量倍数判断严重程度
     * 
     * @param volumeMultiplier 交易量倍数(相对于均值)
     * @return 严重程度
     */
    public static AlertSeverity fromVolumeMultiplier(double volumeMultiplier) {
        if (volumeMultiplier >= 10.0) { // 10倍以上
            return CRITICAL;
        } else if (volumeMultiplier >= 5.0) { // 5倍以上
            return HIGH;
        } else if (volumeMultiplier >= 3.0) { // 3倍以上
            return MEDIUM;
        } else {
            return LOW;
        }
    }
}
