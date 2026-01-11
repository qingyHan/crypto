package com.crypto.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 回测参数 DTO
 * <p>
 * 用于策略回测的参数封装
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestParams implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易对符号
     */
    private String symbol;

    /**
     * 开始时间(时间戳，毫秒)
     */
    private long startTime;

    /**
     * 结束时间(时间戳，毫秒)
     */
    private long endTime;

    /**
     * 初始资金
     */
    private BigDecimal initialCash;

    /**
     * 策略类型: MACD, RSI, 等
     */
    private String strategyType;

    /**
     * 策略参数(JSON格式)
     */
    private Map<String, Object> strategyParams;
}
