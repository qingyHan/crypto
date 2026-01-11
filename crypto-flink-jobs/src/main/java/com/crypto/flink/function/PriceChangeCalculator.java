package com.crypto.flink.function;

import com.crypto.common.entity.KlineData;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.java.tuple.Tuple3;

/**
 * 价格变化计算器
 * 计算时间窗口内的首尾K线,用于计算涨跌幅
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public class PriceChangeCalculator implements
        AggregateFunction<KlineData, PriceChangeAccumulator, Tuple3<String, KlineData, KlineData>> {

    private static final long serialVersionUID = 1L;

    @Override
    public PriceChangeAccumulator createAccumulator() {
        return new PriceChangeAccumulator();
    }

    @Override
    public PriceChangeAccumulator add(KlineData kline, PriceChangeAccumulator acc) {
        if (kline == null) {
            return acc;
        }

        // 记录symbol
        if (acc.symbol == null) {
            acc.symbol = kline.getSymbol();
        }

        // 记录第一条K线
        if (acc.firstKline == null) {
            acc.firstKline = kline;
        }

        // 始终更新最后一条K线
        acc.lastKline = kline;

        return acc;
    }

    @Override
    public Tuple3<String, KlineData, KlineData> getResult(PriceChangeAccumulator acc) {
        return Tuple3.of(acc.symbol, acc.firstKline, acc.lastKline);
    }

    @Override
    public PriceChangeAccumulator merge(PriceChangeAccumulator a, PriceChangeAccumulator b) {
        if (a.symbol == null) {
            return b;
        }
        if (b.symbol == null) {
            return a;
        }

        PriceChangeAccumulator merged = new PriceChangeAccumulator();
        merged.symbol = a.symbol;

        // 选择更早的第一条K线
        merged.firstKline = (a.firstKline.getOpenTime().isBefore(b.firstKline.getOpenTime()))
                ? a.firstKline
                : b.firstKline;

        // 选择更晚的最后一条K线
        merged.lastKline = (a.lastKline.getCloseTime().isAfter(b.lastKline.getCloseTime()))
                ? a.lastKline
                : b.lastKline;

        return merged;
    }
}
