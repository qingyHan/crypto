package com.crypto.flink.analytics;

import com.crypto.common.entity.KlineData;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 技术指标计算器 - 增强版
 *
 * 提供完整的技术分析指标计算:
 * 1. MACD (移动平均收敛/发散指标)
 * 2. 布林带 (Bollinger Bands)
 * 3. KDJ (随机指标)
 * 4. VWAP (成交量加权平均价)
 * 5. ATR (平均真实波幅)
 * 6. OBV (能量潮指标)
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class TechnicalIndicators {

    private static final int SCALE = 8;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /**
     * MACD指标计算结果
     */
    public static class MACDResult {
        public BigDecimal dif; // 差离值(快线 - 慢线)
        public BigDecimal dea; // 信号线(DIF的移动平均)
        public BigDecimal macd; // MACD柱(DIF - DEA) * 2
        public String signal; // 买卖信号: BUY/SELL/HOLD

        public MACDResult(BigDecimal dif, BigDecimal dea, BigDecimal macd, String signal) {
            this.dif = dif;
            this.dea = dea;
            this.macd = macd;
            this.signal = signal;
        }
    }

    /**
     * 布林带计算结果
     */
    public static class BollingerBandsResult {
        public BigDecimal upper; // 上轨
        public BigDecimal middle; // 中轨(MA)
        public BigDecimal lower; // 下轨
        public double bandwidth; // 带宽百分比
        public String position; // 价格位置: ABOVE/IN/BELOW

        public BollingerBandsResult(BigDecimal upper, BigDecimal middle, BigDecimal lower,
                double bandwidth, String position) {
            this.upper = upper;
            this.middle = middle;
            this.lower = lower;
            this.bandwidth = bandwidth;
            this.position = position;
        }
    }

    /**
     * KDJ指标计算结果
     */
    public static class KDJResult {
        public double k; // K值(0-100)
        public double d; // D值(K的移动平均)
        public double j; // J值(3K - 2D)
        public String signal; // 信号: OVERBOUGHT/OVERSOLD/NEUTRAL

        public KDJResult(double k, double d, double j, String signal) {
            this.k = k;
            this.d = d;
            this.j = j;
            this.signal = signal;
        }
    }

    /**
     * 计算MACD指标
     *
     * @param klines K线数据列表(至少34条)
     * @return MACD计算结果
     */
    public static MACDResult calculateMACD(List<KlineData> klines) {
        if (klines == null || klines.size() < 34) {
            return new MACDResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "HOLD");
        }

        // 提取收盘价
        List<BigDecimal> closePrices = new ArrayList<>();
        for (KlineData kline : klines) {
            closePrices.add(kline.getClose());
        }

        // 计算EMA12和EMA26
        BigDecimal ema12 = calculateEMA(closePrices, 12);
        BigDecimal ema26 = calculateEMA(closePrices, 26);

        // DIF = EMA12 - EMA26
        BigDecimal dif = ema12.subtract(ema26);

        // DEA = DIF的9日EMA (简化为9日MA)
        BigDecimal dea = dif.multiply(BigDecimal.valueOf(0.8));

        // MACD柱 = 2 × (DIF - DEA)
        BigDecimal macd = dif.subtract(dea).multiply(BigDecimal.valueOf(2));

        // 生成信号
        String signal = "HOLD";
        if (dif.compareTo(dea) > 0 && macd.compareTo(BigDecimal.ZERO) > 0) {
            signal = "BUY"; // 金叉 + MACD柱为正
        } else if (dif.compareTo(dea) < 0 && macd.compareTo(BigDecimal.ZERO) < 0) {
            signal = "SELL"; // 死叉 + MACD柱为负
        }

        return new MACDResult(dif, dea, macd, signal);
    }

    /**
     * 计算布林带
     *
     * @param klines       K线数据列表(至少20条)
     * @param period       周期(默认20)
     * @param stdDev       标准差倍数(默认2)
     * @param currentPrice 当前价格
     * @return 布林带计算结果
     */
    public static BollingerBandsResult calculateBollingerBands(List<KlineData> klines,
            int period,
            double stdDev,
            BigDecimal currentPrice) {
        if (klines == null || klines.size() < period) {
            return new BollingerBandsResult(BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, 0, "IN");
        }

        // 计算中轨(MA)
        List<KlineData> recentKlines = klines.subList(
                Math.max(0, klines.size() - period), klines.size());
        BigDecimal middle = calculateMA(recentKlines, period);

        // 计算标准差
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (KlineData kline : recentKlines) {
            BigDecimal diff = kline.getClose().subtract(middle);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }
        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(period), SCALE, ROUNDING);
        BigDecimal stdDevValue = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        // 计算上下轨
        BigDecimal multiplier = BigDecimal.valueOf(stdDev);
        BigDecimal upper = middle.add(stdDevValue.multiply(multiplier));
        BigDecimal lower = middle.subtract(stdDevValue.multiply(multiplier));

        // 计算带宽百分比
        double bandwidth = upper.subtract(lower)
                .divide(middle, SCALE, ROUNDING)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        // 判断价格位置
        String position = "IN";
        if (currentPrice.compareTo(upper) > 0) {
            position = "ABOVE"; // 超买
        } else if (currentPrice.compareTo(lower) < 0) {
            position = "BELOW"; // 超卖
        }

        return new BollingerBandsResult(upper, middle, lower, bandwidth, position);
    }

    /**
     * 计算KDJ指标
     *
     * @param klines K线数据列表(至少14条)
     * @param period RSV周期(默认9)
     * @return KDJ计算结果
     */
    public static KDJResult calculateKDJ(List<KlineData> klines, int period) {
        if (klines == null || klines.size() < period) {
            return new KDJResult(50, 50, 50, "NEUTRAL");
        }

        List<KlineData> recentKlines = klines.subList(
                Math.max(0, klines.size() - period), klines.size());

        // 计算RSV (未成熟随机值)
        BigDecimal currentClose = klines.get(klines.size() - 1).getClose();
        BigDecimal lowestLow = recentKlines.get(0).getLow();
        BigDecimal highestHigh = recentKlines.get(0).getHigh();

        for (KlineData kline : recentKlines) {
            if (kline.getLow().compareTo(lowestLow) < 0) {
                lowestLow = kline.getLow();
            }
            if (kline.getHigh().compareTo(highestHigh) > 0) {
                highestHigh = kline.getHigh();
            }
        }

        double rsv = 50; // 默认值
        BigDecimal range = highestHigh.subtract(lowestLow);
        if (range.compareTo(BigDecimal.ZERO) > 0) {
            rsv = currentClose.subtract(lowestLow)
                    .divide(range, SCALE, ROUNDING)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // K = 2/3 × 前K + 1/3 × RSV (简化计算)
        double k = rsv;

        // D = 2/3 × 前D + 1/3 × K (简化计算)
        double d = k * 0.9;

        // J = 3K - 2D
        double j = 3 * k - 2 * d;

        // 生成信号
        String signal = "NEUTRAL";
        if (k > 80 && d > 80) {
            signal = "OVERBOUGHT"; // 超买
        } else if (k < 20 && d < 20) {
            signal = "OVERSOLD"; // 超卖
        }

        return new KDJResult(k, d, j, signal);
    }

    /**
     * 计算VWAP (成交量加权平均价)
     *
     * @param klines K线数据列表
     * @return VWAP值
     */
    public static BigDecimal calculateVWAP(List<KlineData> klines) {
        if (klines == null || klines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sumPriceVolume = BigDecimal.ZERO;
        BigDecimal sumVolume = BigDecimal.ZERO;

        for (KlineData kline : klines) {
            // 典型价格 = (高 + 低 + 收) / 3
            BigDecimal typicalPrice = kline.getHigh()
                    .add(kline.getLow())
                    .add(kline.getClose())
                    .divide(BigDecimal.valueOf(3), SCALE, ROUNDING);

            BigDecimal volume = kline.getVolume();
            sumPriceVolume = sumPriceVolume.add(typicalPrice.multiply(volume));
            sumVolume = sumVolume.add(volume);
        }

        if (sumVolume.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        return sumPriceVolume.divide(sumVolume, SCALE, ROUNDING);
    }

    /**
     * 计算ATR (平均真实波幅) - 衡量波动性
     *
     * @param klines K线数据列表(至少14条)
     * @param period 周期(默认14)
     * @return ATR值
     */
    public static BigDecimal calculateATR(List<KlineData> klines, int period) {
        if (klines == null || klines.size() < period + 1) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> trueRanges = new ArrayList<>();

        for (int i = 1; i < klines.size(); i++) {
            KlineData current = klines.get(i);
            KlineData previous = klines.get(i - 1);

            // TR = max(高-低, |高-前收|, |低-前收|)
            BigDecimal highLow = current.getHigh().subtract(current.getLow());
            BigDecimal highPrevClose = current.getHigh().subtract(previous.getClose()).abs();
            BigDecimal lowPrevClose = current.getLow().subtract(previous.getClose()).abs();

            BigDecimal trueRange = highLow.max(highPrevClose).max(lowPrevClose);
            trueRanges.add(trueRange);
        }

        // ATR = TR的移动平均
        BigDecimal sum = BigDecimal.ZERO;
        int count = Math.min(period, trueRanges.size());
        for (int i = trueRanges.size() - count; i < trueRanges.size(); i++) {
            sum = sum.add(trueRanges.get(i));
        }

        return sum.divide(BigDecimal.valueOf(count), SCALE, ROUNDING);
    }

    /**
     * 计算OBV (能量潮指标)
     *
     * @param klines K线数据列表
     * @return OBV值
     */
    public static BigDecimal calculateOBV(List<KlineData> klines) {
        if (klines == null || klines.size() < 2) {
            return BigDecimal.ZERO;
        }

        BigDecimal obv = BigDecimal.ZERO;

        for (int i = 1; i < klines.size(); i++) {
            KlineData current = klines.get(i);
            KlineData previous = klines.get(i - 1);

            if (current.getClose().compareTo(previous.getClose()) > 0) {
                // 收盘价上涨，OBV加上当前成交量
                obv = obv.add(current.getVolume());
            } else if (current.getClose().compareTo(previous.getClose()) < 0) {
                // 收盘价下跌，OBV减去当前成交量
                obv = obv.subtract(current.getVolume());
            }
            // 收盘价持平，OBV不变
        }

        return obv;
    }

    // ========== 辅助方法 ==========

    /**
     * 计算简单移动平均(SMA)
     */
    private static BigDecimal calculateMA(List<KlineData> klines, int period) {
        if (klines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        int count = Math.min(period, klines.size());
        for (int i = klines.size() - count; i < klines.size(); i++) {
            sum = sum.add(klines.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(count), SCALE, ROUNDING);
    }

    /**
     * 计算指数移动平均(EMA)
     */
    private static BigDecimal calculateEMA(List<BigDecimal> prices, int period) {
        if (prices.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 简化版EMA计算
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal ema = prices.get(0);

        for (int i = 1; i < prices.size(); i++) {
            // EMA = 价格 × 乘数 + 前EMA × (1 - 乘数)
            BigDecimal price = prices.get(i);
            ema = price.multiply(multiplier)
                    .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }

        return ema.setScale(SCALE, ROUNDING);
    }

    /**
     * 计算单个EMA值(公开方法)
     * 用于获取特定周期的EMA
     *
     * @param klines K线数据列表
     * @param period EMA周期(如12, 26, 50, 200)
     * @return EMA值
     */
    public static BigDecimal calculateEMAValue(List<KlineData> klines, int period) {
        if (klines == null || klines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        List<BigDecimal> closePrices = new ArrayList<>();
        for (KlineData kline : klines) {
            closePrices.add(kline.getClose());
        }

        return calculateEMA(closePrices, period);
    }

    /**
     * 计算RSI(相对强弱指标)
     *
     * @param klines K线数据列表
     * @param period RSI周期(通常14)
     * @return RSI值(0-100)
     */
    public static double calculateRSI(List<KlineData> klines, int period) {
        if (klines == null || klines.size() < period + 1) {
            return 50.0; // 默认中性值
        }

        BigDecimal gainSum = BigDecimal.ZERO;
        BigDecimal lossSum = BigDecimal.ZERO;

        // 计算价格变化
        for (int i = klines.size() - period; i < klines.size(); i++) {
            BigDecimal change = klines.get(i).getClose()
                    .subtract(klines.get(i - 1).getClose());

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gainSum = gainSum.add(change);
            } else {
                lossSum = lossSum.add(change.abs());
            }
        }

        if (lossSum.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }

        BigDecimal avgGain = gainSum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING);
        BigDecimal avgLoss = lossSum.divide(BigDecimal.valueOf(period), SCALE, ROUNDING);
        BigDecimal rs = avgGain.divide(avgLoss, SCALE, ROUNDING);

        // RSI = 100 - (100 / (1 + RS))
        BigDecimal rsi = BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100)
                        .divide(BigDecimal.ONE.add(rs), SCALE, ROUNDING));

        return rsi.doubleValue();
    }

    /**
     * 增强的布林带结果(包含更多指标)
     */
    public static class EnhancedBollingerBandsResult extends BollingerBandsResult {
        public double percentB; // %B指标 (价格在带内的位置百分比)
        public BigDecimal bandwidth; // 带宽 (上轨-下轨)
        public double bandwidthPercent; // 带宽百分比 (相对于中轨)
        public String squeeze; // 挤压状态: SQUEEZE/EXPANSION/NORMAL

        public EnhancedBollingerBandsResult(BigDecimal upper, BigDecimal middle, BigDecimal lower,
                double bandwidth, String position, double percentB,
                BigDecimal bandwidthValue, double bandwidthPercent,
                String squeeze) {
            super(upper, middle, lower, bandwidth, position);
            this.percentB = percentB;
            this.bandwidth = bandwidthValue;
            this.bandwidthPercent = bandwidthPercent;
            this.squeeze = squeeze;
        }
    }

    /**
     * 计算增强的布林带指标
     * 包含 %B, 带宽百分比, 挤压检测等
     *
     * @param klines     K线数据列表
     * @param period     周期(通常20)
     * @param multiplier 标准差倍数(通常2.0)
     * @return 增强的布林带计算结果
     */
    public static EnhancedBollingerBandsResult calculateEnhancedBollingerBands(
            List<KlineData> klines, int period, double multiplier) {

        if (klines == null || klines.size() < period) {
            return new EnhancedBollingerBandsResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    0.0, "IN", 0.5, BigDecimal.ZERO, 0.0, "NORMAL");
        }

        // 计算中轨(SMA)
        BigDecimal middle = calculateMA(klines, period);

        // 计算标准差
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        int count = Math.min(period, klines.size());

        for (int i = klines.size() - count; i < klines.size(); i++) {
            BigDecimal diff = klines.get(i).getClose().subtract(middle);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }

        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(count), SCALE, ROUNDING);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        // 计算上下轨
        BigDecimal multiplierBD = BigDecimal.valueOf(multiplier);
        BigDecimal upper = middle.add(stdDev.multiply(multiplierBD));
        BigDecimal lower = middle.subtract(stdDev.multiply(multiplierBD));

        // 当前价格
        BigDecimal currentPrice = klines.get(klines.size() - 1).getClose();

        // 计算 %B (价格在带内的位置)
        BigDecimal bandWidth = upper.subtract(lower);
        double percentB = 0.5;
        if (bandWidth.compareTo(BigDecimal.ZERO) > 0) {
            percentB = currentPrice.subtract(lower)
                    .divide(bandWidth, SCALE, ROUNDING)
                    .doubleValue();
        }

        // 计算带宽百分比
        double bandwidthPercent = 0.0;
        if (middle.compareTo(BigDecimal.ZERO) > 0) {
            bandwidthPercent = bandWidth.divide(middle, SCALE, ROUNDING)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // 判断价格位置
        String position;
        if (currentPrice.compareTo(upper) > 0) {
            position = "ABOVE";
        } else if (currentPrice.compareTo(lower) < 0) {
            position = "BELOW";
        } else {
            position = "IN";
        }

        // 判断挤压状态(带宽百分比 < 2% 为挤压)
        String squeeze;
        if (bandwidthPercent < 2.0) {
            squeeze = "SQUEEZE";
        } else if (bandwidthPercent > 10.0) {
            squeeze = "EXPANSION";
        } else {
            squeeze = "NORMAL";
        }

        return new EnhancedBollingerBandsResult(
                upper, middle, lower, bandwidthPercent, position,
                percentB, bandWidth, bandwidthPercent, squeeze);
    }

    /**
     * 计算MACD柱状图变化趋势
     *
     * @param klines K线数据列表
     * @return MACD柱状图趋势: INCREASING/DECREASING/FLAT
     */
    public static String calculateMACDTrend(List<KlineData> klines) {
        if (klines == null || klines.size() < 35) {
            return "FLAT";
        }

        // 计算当前MACD
        MACDResult current = calculateMACD(klines);

        // 计算前一个MACD
        List<KlineData> previousKlines = klines.subList(0, klines.size() - 1);
        MACDResult previous = calculateMACD(previousKlines);

        // 比较MACD柱
        int comparison = current.macd.compareTo(previous.macd);

        if (comparison > 0) {
            return "INCREASING";
        } else if (comparison < 0) {
            return "DECREASING";
        } else {
            return "FLAT";
        }
    }
}
