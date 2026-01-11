package com.crypto.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 资金曲线点 DTO
 * <p>
 * 用于回测结果中资金曲线的数据点传输
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EquityPoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 总资产
     */
    private BigDecimal equity;

    /**
     * 现金
     */
    private BigDecimal cash;

    /**
     * 持仓价值
     */
    private BigDecimal position;
}
