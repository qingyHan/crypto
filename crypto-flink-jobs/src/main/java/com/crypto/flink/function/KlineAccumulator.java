package com.crypto.flink.function;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * K线累加器
 * 用于聚合交易数据生成K线
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
public class KlineAccumulator implements Serializable {

    private static final long serialVersionUID = 1L;

    String symbol;
    LocalDateTime openTime;
    LocalDateTime closeTime;
    BigDecimal open;
    BigDecimal high;
    BigDecimal low;
    BigDecimal close;
    BigDecimal volume = BigDecimal.ZERO;
    BigDecimal quoteVolume = BigDecimal.ZERO;
    int trades = 0;

    /**
     * 记录第一笔和最后一笔交易的事件时间，用于处理乱序数据
     */
    Long firstTradeTime;
    Long lastTradeTime;
}
