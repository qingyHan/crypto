package com.crypto.common.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * K线数据实体类 (OHLCV)
 * <p>
 * 代表金融市场标准的时间周期聚合数据（Candlestick）。包含开高低收价格、成交量等核心指标。
 * </p>
 * 
 * <h3>数据来源与存储：</h3>
 * <ul>
 *   <li><strong>来源</strong>：由 Flink 作业从实时 {@link TradeEvent} 流聚合生成，或从 OKX API 补录。</li>
 *   <li><strong>存储</strong>：ClickHouse (历史分析) + Redis (实时缓存)。</li>
 * </ul>
 * 
 * <h3>时间周期：</h3>
 * 支持 1m, 5m, 15m, 1h, 4h, 1d 等标准周期。
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KlineData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易对符号
     */
    private String symbol;

    /**
     * 时间周期: 1m, 5m, 15m, 1h, 1d
     */
    private String interval;

    /**
     * 开盘时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime openTime;

    /**
     * 收盘时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closeTime;

    /**
     * 开盘价
     */
    private BigDecimal open;

    /**
     * 最高价
     */
    private BigDecimal high;

    /**
     * 最低价
     */
    private BigDecimal low;

    /**
     * 收盘价
     */
    private BigDecimal close;

    /**
     * 成交量(币的数量)
     */
    private BigDecimal volume;

    /**
     * 成交额(USDT金额)
     */
    private BigDecimal quoteVolume;

    /**
     * 成交笔数
     */
    private Integer trades;

    /**
     * 插入时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime insertTime;

    /**
     * 计算涨跌幅百分比
     * 
     * @return (收盘价 - 开盘价) / 开盘价 × 100%
     */
    public BigDecimal calculateChangePercent() {
        if (open == null || close == null || open.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return close.subtract(open)
                .divide(open, 6, RoundingMode.HALF_UP)// 保留6位小数，四舍五入
                .multiply(new BigDecimal("100"));
    }

    /**
     * 计算振幅百分比
     * 
     * @return (最高价 - 最低价) / 开盘价 × 100%
     */
    public BigDecimal calculateAmplitude() {
        if (open == null || high == null || low == null || open.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return high.subtract(low)
                .divide(open, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
    }

    /**
     * 判断是否为阳线
     * 
     * @return true-阳线, false-阴线或平
     */
    public boolean isBullish() {
        return close != null && open != null && close.compareTo(open) > 0;
    }

    /**
     * 验证数据完整性
     * 
     * @return true-有效, false-无效
     */
    public boolean isValid() {
        return symbol != null && !symbol.isEmpty()
                && interval != null && !interval.isEmpty()
                && openTime != null && closeTime != null
                && open != null && high != null && low != null && close != null
                && volume != null && trades != null;
    }
}
