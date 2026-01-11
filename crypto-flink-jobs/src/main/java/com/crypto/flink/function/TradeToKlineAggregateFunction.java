package com.crypto.flink.function;

import com.crypto.common.entity.KlineData;
import com.crypto.common.entity.TradeEvent;
import com.crypto.common.utils.DateTimeUtil;
import org.apache.flink.api.common.functions.AggregateFunction;

import java.math.BigDecimal;

/**
 * 交易事件聚合为K线的聚合函数 - Flink窗口聚合核心逻辑
 * 
 * 这是Flink流处理的核心聚合函数，负责将窗口内的多个TradeEvent聚合成一个KlineData对象。
 * 
 * 聚合逻辑说明：
 * - Open（开盘价）：窗口内第一笔交易的价格
 * - High（最高价）：窗口内所有交易价格的最大值
 * - Low（最低价）：窗口内所有交易价格的最小值
 * - Close（收盘价）：窗口内最后一笔交易的价格（持续更新）
 * - Volume（成交量）：窗口内所有交易的币数量总和（累加）
 * - QuoteVolume（成交额）：窗口内所有交易的USDT金额总和（price * quantity累加）
 * - Trades（成交笔数）：窗口内的交易次数（计数）
 * 
 * Flink聚合函数接口实现：
 * - createAccumulator()：创建累加器（窗口开始时调用）
 * - add()：将新的交易事件添加到累加器（窗口内每来一条数据调用）
 * - getResult()：从累加器生成最终的K线数据（窗口结束时调用）
 * - merge()：合并两个累加器（窗口合并时调用，如会话窗口）
 * 
 * 使用场景：在KlineAggregationJob中，通过.window().aggregate()方式使用
 * 
 * 示例：假设1分钟内收到3笔交易
 * 交易1：价格92000，数量0.1 → 更新open=92000, close=92000, volume=0.1
 * 交易2：价格92500，数量0.2 → 更新high=92500, close=92500, volume=0.3
 * 交易3：价格92300，数量0.15 → 更新low=92300, close=92300, volume=0.45
 * 窗口结束时，生成K线：open=92000, high=92500, low=92300, close=92300, volume=0.45
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public class TradeToKlineAggregateFunction implements AggregateFunction<TradeEvent, KlineAccumulator, KlineData> {

    private static final long serialVersionUID = 1L;

    @Override
    public KlineAccumulator createAccumulator() {
        return new KlineAccumulator();
    }

    /**
     * 将新的交易事件添加到累加器
     * 
     * 这是聚合的核心方法，窗口内每来一条交易数据都会调用此方法。
     * 方法会逐步构建K线的各个字段：
     * 
     * 处理逻辑：
     * 1. 数据验证：检查交易事件是否有效，无效则直接返回
     * 2. 初始化：如果是窗口内的第一条数据，设置symbol和openTime
     * 3. 开盘价：第一条数据的价格作为开盘价（open）
     * 4. 最高价：持续比较，保留最大值（high）
     * 5. 最低价：持续比较，保留最小值（low）
     * 6. 收盘价：每次更新为最新价格（close），窗口结束时就是最后一笔的价格
     * 7. 成交量：累加所有交易的币数量（volume）
     * 8. 成交额：累加所有交易的USDT金额（quoteVolume = price * quantity）
     * 9. 成交笔数：计数器递增（trades）
     * 10. 收盘时间：更新为最新交易的时间（closeTime）
     * 
     * @param trade       新的交易事件
     * @param accumulator 累加器对象，存储窗口内的聚合状态
     * @return 更新后的累加器
     */
    @Override
    public KlineAccumulator add(TradeEvent trade, KlineAccumulator accumulator) {
        if (trade == null || !trade.isValid()) {
            return accumulator;
        }

        // ============ 初始化：窗口内的第一条数据 ============
        if (accumulator.symbol == null) {
            accumulator.symbol = trade.getSymbol();
            accumulator.openTime = DateTimeUtil.getMinuteStart(trade.getEventTime());

            // 初始化 Open/Close 相关的追踪时间戳
            accumulator.firstTradeTime = trade.getEventTime();
            accumulator.lastTradeTime = trade.getEventTime();
        }

        // ============ OHLC价格字段更新 ============

        // 开盘价逻辑：根据事件时间判断是否是“更早”的一笔交易
        if (accumulator.open == null || trade.getEventTime() < accumulator.firstTradeTime) {
            accumulator.open = trade.getPrice();
            accumulator.firstTradeTime = trade.getEventTime();
        }

        // 收盘价逻辑：根据事件时间判断是否是“更晚”的一笔交易
        // 注意：即使是乱序到达的“晚”数据，只要它的发生时间比当前记录的lastTradeTime晚，它就是新的Close
        if (accumulator.close == null || trade.getEventTime() >= accumulator.lastTradeTime) {
            accumulator.close = trade.getPrice();
            accumulator.lastTradeTime = trade.getEventTime();
        }

        // 最高价：始终保留最大值
        if (accumulator.high == null || trade.getPrice().compareTo(accumulator.high) > 0) {
            accumulator.high = trade.getPrice();
        }

        // 最低价：始终保留最小值
        if (accumulator.low == null || trade.getPrice().compareTo(accumulator.low) < 0) {
            accumulator.low = trade.getPrice();
        }

        // ============ 成交量相关字段累加 ============
        // 累计成交量
        if (accumulator.volume == null) {
            accumulator.volume = BigDecimal.ZERO;
        }
        accumulator.volume = accumulator.volume.add(trade.getQuantity());

        // 累计成交额
        if (accumulator.quoteVolume == null) {
            accumulator.quoteVolume = BigDecimal.ZERO;
        }
        accumulator.quoteVolume = accumulator.quoteVolume.add(trade.calculateAmount());

        // 累计交易笔数
        accumulator.trades++;

        // 更新收盘时间（取窗口内最大的事件时间）
        if (accumulator.closeTime == null ||
                DateTimeUtil.timestampToLocalDateTime(trade.getEventTime()).isAfter(accumulator.closeTime)) {
            accumulator.closeTime = DateTimeUtil.timestampToLocalDateTime(trade.getEventTime());
        }

        return accumulator;
    }

    @Override
    public KlineData getResult(KlineAccumulator accumulator) {
        return KlineData.builder()
                .symbol(accumulator.symbol)
                .openTime(accumulator.openTime)
                .closeTime(accumulator.closeTime)
                .open(accumulator.open != null ? accumulator.open : BigDecimal.ZERO)
                .high(accumulator.high != null ? accumulator.high : BigDecimal.ZERO)
                .low(accumulator.low != null ? accumulator.low : BigDecimal.ZERO)
                .close(accumulator.close != null ? accumulator.close : BigDecimal.ZERO)
                .volume(accumulator.volume != null ? accumulator.volume : BigDecimal.ZERO)
                .quoteVolume(accumulator.quoteVolume != null ? accumulator.quoteVolume : BigDecimal.ZERO)
                .trades(accumulator.trades)
                .build();
    }

    @Override
    public KlineAccumulator merge(KlineAccumulator a, KlineAccumulator b) {
        if (a.symbol == null) {
            return b;
        }
        if (b.symbol == null) {
            return a;
        }

        KlineAccumulator merged = new KlineAccumulator();
        merged.symbol = a.symbol;
        // 窗口时间：取最早的开始时间
        merged.openTime = a.openTime.isBefore(b.openTime) ? a.openTime : b.openTime;
        // 收盘时间：取最晚的结束时间
        merged.closeTime = a.closeTime.isAfter(b.closeTime) ? a.closeTime : b.closeTime;

        // Open: 取发生时间最早的 price
        if (a.firstTradeTime == null && b.firstTradeTime == null) {
            merged.open = a.open != null ? a.open : b.open;
        } else if (a.firstTradeTime == null) {
            merged.open = b.open;
            merged.firstTradeTime = b.firstTradeTime;
        } else if (b.firstTradeTime == null) {
            merged.open = a.open;
            merged.firstTradeTime = a.firstTradeTime;
        } else {
            if (a.firstTradeTime <= b.firstTradeTime) {
                merged.open = a.open;
                merged.firstTradeTime = a.firstTradeTime;
            } else {
                merged.open = b.open;
                merged.firstTradeTime = b.firstTradeTime;
            }
        }

        // Close: 取发生时间最晚的 price
        if (a.lastTradeTime == null && b.lastTradeTime == null) {
            merged.close = a.close != null ? a.close : b.close;
        } else if (a.lastTradeTime == null) {
            merged.close = b.close;
            merged.lastTradeTime = b.lastTradeTime;
        } else if (b.lastTradeTime == null) {
            merged.close = a.close;
            merged.lastTradeTime = a.lastTradeTime;
        } else {
            if (a.lastTradeTime >= b.lastTradeTime) {
                merged.close = a.close;
                merged.lastTradeTime = a.lastTradeTime;
            } else {
                merged.close = b.close;
                merged.lastTradeTime = b.lastTradeTime;
            }
        }

        // High: 取两个累加器中的最大值
        merged.high = (a.high != null && b.high != null)
                ? a.high.max(b.high)
                : (a.high != null ? a.high : b.high);

        // Low: 取两个累加器中的最小值
        merged.low = (a.low != null && b.low != null)
                ? a.low.min(b.low)
                : (a.low != null ? a.low : b.low);

        // 累计成交量和成交额
        merged.volume = (a.volume != null ? a.volume : BigDecimal.ZERO)
                .add(b.volume != null ? b.volume : BigDecimal.ZERO);
        merged.quoteVolume = (a.quoteVolume != null ? a.quoteVolume : BigDecimal.ZERO)
                .add(b.quoteVolume != null ? b.quoteVolume : BigDecimal.ZERO);
        merged.trades = a.trades + b.trades;

        return merged;
    }
}
