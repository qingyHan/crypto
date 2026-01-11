package com.crypto.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 交易记录 DTO
 * <p>
 * 用于回测结果的交易记录传输
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易时间戳
     */
    private long timestamp;

    /**
     * 交易类型: BUY/SELL
     */
    private String type;

    /**
     * 成交价格
     */
    private BigDecimal price;

    /**
     * 成交数量
     */
    private BigDecimal quantity;

    /**
     * 此交易的盈亏
     */
    private BigDecimal profit;

    /**
     * 交易信号
     */
    private String signal;
}
