package com.crypto.common.enums;

import lombok.Getter;

/**
 * 预警类型枚举
 * <p>
 * 标识系统支持的各类异常模式。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Getter
public enum AlertType {

    /**
     * 价格突涨
     */
    PRICE_SPIKE("PRICE_SPIKE", "价格突涨"),

    /**
     * 价格暴跌
     */
    PRICE_DROP("PRICE_DROP", "价格暴跌"),

    /**
     * 交易量异常
     */
    VOLUME_ANOMALY("VOLUME_ANOMALY", "交易量异常"),

    /**
     * 巨鲸交易
     */
    WHALE_TRADE("WHALE_TRADE", "巨鲸交易"),

    /**
     * 价格跨交易所套利机会
     */
    ARBITRAGE_OPPORTUNITY("ARBITRAGE_OPPORTUNITY", "套利机会"),

    /**
     * 波动率异常
     */
    VOLATILITY_ANOMALY("VOLATILITY_ANOMALY", "波动率异常"),

    /**
     * 市场深度失衡
     */
    MARKET_DEPTH_IMBALANCE("MARKET_DEPTH_IMBALANCE", "市场深度失衡");

    private final String code;
    private final String description;

    AlertType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据code获取枚举
     */
    public static AlertType fromCode(String code) {
        for (AlertType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown AlertType code: " + code);
    }
}
