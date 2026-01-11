package com.crypto.flink.function;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 交易量累加器
 * 用于聚合窗口内的交易量统计信息
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
public class VolumeAccumulator implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易对符号
     */
    private String symbol;

    /**
     * 总交易量（金额）
     */
    private BigDecimal totalVolume = BigDecimal.ZERO;

    /**
     * 交易笔数
     */
    private Long tradeCount = 0L;

    /**
     * 最大单笔交易金额
     */
    private BigDecimal maxTradeAmount = BigDecimal.ZERO;

    /**
     * 最小单笔交易金额
     */
    private BigDecimal minTradeAmount = BigDecimal.ZERO;
}
