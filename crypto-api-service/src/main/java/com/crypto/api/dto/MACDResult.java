package com.crypto.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * MACD 计算结果 DTO
 * <p>
 * 用于技术指标 MACD 的计算结果传输
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MACDResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * MACD 值
     */
    private double macd;

    /**
     * Signal 线
     */
    private double signal;

    /**
     * 柱状图（MACD - Signal）
     */
    private double histogram;

    /**
     * 信号类型: BUY, SELL, HOLD
     */
    private String signalType;
}
