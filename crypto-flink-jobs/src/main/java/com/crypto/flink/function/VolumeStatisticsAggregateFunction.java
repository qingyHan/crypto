package com.crypto.flink.function;

import com.crypto.common.entity.TradeEvent;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple4;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 交易量统计聚合函数
 * 计算窗口内的交易量统计信息
 *
 * 输入: TradeEvent
 * 累加器: VolumeAccumulator
 * 输出: Tuple4<symbol, currentVolume, avgVolume, tradeCount>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public class VolumeStatisticsAggregateFunction
        implements AggregateFunction<TradeEvent, VolumeAccumulator, Tuple4<String, BigDecimal, BigDecimal, Long>> {

    @Override
    public VolumeAccumulator createAccumulator() {
        return new VolumeAccumulator();
    }

    @Override
    public VolumeAccumulator add(TradeEvent trade, VolumeAccumulator accumulator) {
        if (trade == null || !trade.isValid()) {
            return accumulator;
        }

        // 设置交易对符号
        if (accumulator.getSymbol() == null) {
            accumulator.setSymbol(trade.getSymbol());
        }

        // 累加交易金额
        BigDecimal tradeAmount = trade.calculateAmount();
        accumulator.setTotalVolume(accumulator.getTotalVolume().add(tradeAmount));

        // 增加交易计数
        accumulator.setTradeCount(accumulator.getTradeCount() + 1);

        // 更新最小和最大交易量（用于计算标准差）
        if (tradeAmount.compareTo(accumulator.getMaxTradeAmount()) > 0) {
            accumulator.setMaxTradeAmount(tradeAmount);
        }
        if (accumulator.getMinTradeAmount().compareTo(BigDecimal.ZERO) == 0
                || tradeAmount.compareTo(accumulator.getMinTradeAmount()) < 0) {
            accumulator.setMinTradeAmount(tradeAmount);
        }

        return accumulator;
    }

    @Override
    public Tuple4<String, BigDecimal, BigDecimal, Long> getResult(VolumeAccumulator accumulator) {
        String symbol = accumulator.getSymbol();
        BigDecimal totalVolume = accumulator.getTotalVolume();
        Long tradeCount = accumulator.getTradeCount();

        // 计算平均交易量（这里简化处理，使用窗口内总交易量作为当前值）
        // 实际应用中可以维护历史窗口的均值
        BigDecimal avgVolume = tradeCount > 0
                ? totalVolume.divide(new BigDecimal(tradeCount), 8, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        // 返回: symbol, 当前总交易量, 平均单笔交易量, 交易笔数
        return Tuple4.of(symbol, totalVolume, avgVolume, tradeCount);
    }

    @Override
    public VolumeAccumulator merge(VolumeAccumulator acc1, VolumeAccumulator acc2) {
        if (acc1.getSymbol() == null) {
            acc1.setSymbol(acc2.getSymbol());
        }

        acc1.setTotalVolume(acc1.getTotalVolume().add(acc2.getTotalVolume()));
        acc1.setTradeCount(acc1.getTradeCount() + acc2.getTradeCount());

        if (acc2.getMaxTradeAmount().compareTo(acc1.getMaxTradeAmount()) > 0) {
            acc1.setMaxTradeAmount(acc2.getMaxTradeAmount());
        }

        if (acc1.getMinTradeAmount().compareTo(BigDecimal.ZERO) == 0
                || (acc2.getMinTradeAmount().compareTo(BigDecimal.ZERO) > 0
                && acc2.getMinTradeAmount().compareTo(acc1.getMinTradeAmount()) < 0)) {
            acc1.setMinTradeAmount(acc2.getMinTradeAmount());
        }

        return acc1;
    }
}
