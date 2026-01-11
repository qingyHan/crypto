package com.crypto.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测结果 DTO
 * <p>
 * 用于策略回测结果的传输和展示
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 策略名称
     */
    private String strategyName;

    /**
     * 总收益率(%)
     */
    private double totalReturn;

    /**
     * 年化收益率(%)
     */
    private double annualizedReturn;

    /**
     * 最大回撤(%)
     */
    private double maxDrawdown;

    /**
     * 夏普比率
     */
    private double sharpeRatio;

    /**
     * 总交易次数
     */
    private int totalTrades;

    /**
     * 盈利交易次数
     */
    private int winningTrades;

    /**
     * 亏损交易次数
     */
    private int losingTrades;

    /**
     * 胜率(%)
     */
    private double winRate;

    /**
     * 平均盈利(%)
     */
    private double avgProfit;

    /**
     * 平均亏损(%)
     */
    private double avgLoss;

    /**
     * 交易记录列表
     */
    @Builder.Default
    private List<Trade> trades = new ArrayList<>();

    /**
     * 资金曲线数据点列表
     */
    @Builder.Default
    private List<EquityPoint> equityCurve = new ArrayList<>();
}
