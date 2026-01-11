package com.crypto.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 市场数据DTO
 * <p>
 * 用于市场行情数据的传输和展示
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易对符号
     */
    private String symbol;

    /**
     * 最新价格
     */
    private BigDecimal price;

    /**
     * 24小时涨跌幅(百分比)
     */
    private BigDecimal change24h;

    /**
     * 24小时最高价
     */
    private BigDecimal high24h;

    /**
     * 24小时最低价
     */
    private BigDecimal low24h;

    /**
     * 24小时成交量
     */
    private BigDecimal volume24h;

    /**
     * 24小时成交额(USDT)
     */
    private BigDecimal quoteVolume24h;

    /**
     * 数据时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * 实时价格数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PriceData implements Serializable {
        /**
         * 交易对符号
         */
        private String symbol;

        /**
         * 当前价格
         */
        private BigDecimal price;

        /**
         * 涨跌幅(百分比)
         */
        private BigDecimal changePercent;

        /**
         * 更新时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime updateTime;
    }

    /**
     * K线数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KlineData implements Serializable {
        /**
         * 开盘时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime openTime;

        /**
         * 开盘价
         */
        private String open;

        /**
         * 最高价
         */
        private String high;

        /**
         * 最低价
         */
        private String low;

        /**
         * 收盘价
         */
        private String close;

        /**
         * 成交量
         */
        private String volume;

        /**
         * 成交额
         */
        private String quoteVolume;

        /**
         * 成交笔数
         */
        private Integer trades;
    }

    /**
     * 交易对信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class SymbolInfo implements Serializable {
        /**
         * 交易对符号
         */
        private String symbol;

        /**
         * 基础货币
         */
        private String baseAsset;

        /**
         * 计价货币
         */
        private String quoteAsset;

        /**
         * 交易对状态: TRADING, HALT
         */
        private String status;

        /**
         * 是否启用
         */
        private Boolean enabled;

        /**
         * 最新价格
         */
        private BigDecimal lastPrice;

        /**
         * 24小时涨跌幅
         */
        private BigDecimal change24h;

        /**
         * 24小时成交量
         */
        private BigDecimal volume24h;

        /**
         * 24小时最高价
         */
        private BigDecimal high24h;

        /**
         * 24小时最低价
         */
        private BigDecimal low24h;
    }

    /**
     * 市场统计数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MarketStatistics implements Serializable {
        /**
         * 交易对总数
         */
        private Integer totalSymbols;

        /**
         * 上涨交易对数量
         */
        private Integer risingCount;

        /**
         * 下跌交易对数量
         */
        private Integer fallingCount;

        /**
         * 平盘交易对数量
         */
        private Integer flatCount;

        /**
         * 总成交额(24h)
         */
        private BigDecimal totalVolume24h;

        /**
         * 平均涨跌幅
         */
        private BigDecimal avgChangePercent;

        /**
         * 最大涨幅交易对
         */
        private SymbolInfo topGainer;

        /**
         * 最大跌幅交易对
         */
        private SymbolInfo topLoser;

        /**
         * 统计时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime statisticsTime;
    }

    /**
     * K线查询请求
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KlineQueryRequest implements Serializable {
        /**
         * 交易对符号
         */
        private String symbol;

        /**
         * K线周期: 1m, 5m, 15m, 30m, 1h, 4h, 1d
         */
        private String interval;

        /**
         * 开始时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime startTime;

        /**
         * 结束时间
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime endTime;

        /**
         * 限制数量
         */
        private Integer limit;
    }

    /**
     * K线响应
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KlineResponse implements Serializable {
        /**
         * 交易对符号
         */
        private String symbol;

        /**
         * K线周期
         */
        private String interval;

        /**
         * K线数据列表
         */
        private List<KlineData> data;

        /**
         * 数据总数
         */
        private Integer count;
    }
}
