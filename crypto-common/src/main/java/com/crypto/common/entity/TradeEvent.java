package com.crypto.common.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 实时交易事件实体类
 * <p>
 * 系统的原子数据单元，代表交易所撮合引擎产生的一笔真实成交记录。
 * </p>
 * 
 * <h3>数据流向：</h3>
 * <p>
 * 交易所 WebSocket &rarr; {@code TradeEvent} &rarr; Kafka (crypto-trades) &rarr; Flink 聚合/风控
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件类型
     */
    @JsonProperty("e")
    private String eventType;

    /**
     * 事件时间(毫秒时间戳)
     */
    @JsonProperty("E")
    private Long eventTime;

    /**
     * 交易对符号
     */
    @JsonProperty("s")
    private String symbol;

    /**
     * 交易ID
     */
    @JsonProperty("t")
    private Long tradeId;

    /**
     * 成交价格
     */
    @JsonProperty("p")
    private BigDecimal price;

    /**
     * 成交数量
     */
    @JsonProperty("q")
    private BigDecimal quantity;

    /**
     * 买方订单ID
     */
    @JsonProperty("b")
    private Long buyerOrderId;

    /**
     * 卖方订单ID
     */
    @JsonProperty("a")
    private Long sellerOrderId;

    /**
     * 成交时间
     */
    @JsonProperty("T")
    private Long tradeTime;

    /**
     * 买方是否为挂单方
     */
    @JsonProperty("m")
    private Boolean isBuyerMaker;

    /**
     * 计算成交金额
     * 
     * @return 成交金额(价格 * 数量)
     */
    public BigDecimal calculateAmount() {
        if (price == null || quantity == null) {
            return BigDecimal.ZERO;// 防止空指针异常
        }
        return price.multiply(quantity);
    }

    /**
     * 验证数据有效性
     * 
     * @return true-有效, false-无效
     */
    public boolean isValid() {
        return symbol != null && !symbol.isEmpty() // 交易对不能为空
                && price != null && price.compareTo(BigDecimal.ZERO) > 0 // 价格必须大于0
                && quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0 // 数量必须大于0
                && eventTime != null && eventTime > 0; // 时间戳必须有效
    }
}
