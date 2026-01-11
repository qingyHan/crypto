package com.crypto.flink.function;

import com.crypto.common.entity.KlineData;
import lombok.Data;

import java.io.Serializable;

/**
 * 价格变化累加器
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
public class PriceChangeAccumulator implements Serializable {

    private static final long serialVersionUID = 1L;

    String symbol;
    KlineData firstKline;
    KlineData lastKline;
}
