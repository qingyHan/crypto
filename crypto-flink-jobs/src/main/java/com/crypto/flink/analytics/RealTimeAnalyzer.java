package com.crypto.flink.analytics;

import com.crypto.common.entity.KlineData;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * 实时数据分析器 - 挖掘数据价值
 * 
 * 核心功能：
 * 1. 趋势分析：识别上涨/下跌/震荡趋势
 * 2. 异常检测：价格突变、交易量异常
 * 3. 波动率计算：评估市场风险
 * 4. 支撑位/压力位识别
 * 5. 动量指标计算
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Data
public class RealTimeAnalyzer {

    private static final int TREND_WINDOW = 20; // 趋势分析窗口：20条K线
    private static final int VOLATILITY_WINDOW = 30; // 波动率计算窗口：30条K线
    private static final BigDecimal SPIKE_THRESHOLD = new BigDecimal("0.03"); // 3% 价格突变阈值

    private final String symbol;
    private final LinkedList<KlineData> dataWindow;

    public RealTimeAnalyzer(String symbol) {
        this.symbol = symbol;
        this.dataWindow = new LinkedList<>();
    }

    /**
     * 添加新数据并触发分析
     */
    public AnalysisResult analyze(KlineData newData) {
        dataWindow.add(newData);

        // 保持窗口大小
        while (dataWindow.size() > Math.max(TREND_WINDOW, VOLATILITY_WINDOW)) {
            dataWindow.removeFirst();
        }

        if (dataWindow.size() < 10) {
            return AnalysisResult.insufficient(symbol);
        }

        AnalysisResult result = new AnalysisResult();
        result.setSymbol(symbol);
        result.setCurrentPrice(newData.getClose());
        result.setTimestamp(
                newData.getCloseTime() != null ? newData.getCloseTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : null);

        // 1. 趋势分析
        result.setTrend(analyzeTrend());
        result.setTrendStrength(calculateTrendStrength());

        // 2. 异常检测
        result.setPriceSpike(detectPriceSpike(newData));
        result.setVolumeAnomaly(detectVolumeAnomaly(newData));

        // 3. 波动率计算
        result.setVolatility(calculateVolatility());
        result.setRiskLevel(assessRiskLevel(result.getVolatility()));

        // 4. 支撑位/压力位
        result.setSupportLevel(findSupportLevel());
        result.setResistanceLevel(findResistanceLevel());

        // 5. 动量指标
        result.setMomentum(calculateMomentum());
        result.setRsi(calculateRSI());

        return result;
    }

    /**
     * 趋势分析：基于移动平均线
     */
    private TrendDirection analyzeTrend() {
        if (dataWindow.size() < TREND_WINDOW) {
            return TrendDirection.UNKNOWN;
        }

        List<KlineData> trendData = dataWindow.subList(
                Math.max(0, dataWindow.size() - TREND_WINDOW), dataWindow.size());

        BigDecimal ma5 = calculateMA(trendData, 5);
        BigDecimal ma10 = calculateMA(trendData, 10);
        BigDecimal ma20 = calculateMA(trendData, 20);

        // 多头排列：MA5 > MA10 > MA20
        if (ma5.compareTo(ma10) > 0 && ma10.compareTo(ma20) > 0) {
            return TrendDirection.UPTREND;
        }
        // 空头排列：MA5 < MA10 < MA20
        else if (ma5.compareTo(ma10) < 0 && ma10.compareTo(ma20) < 0) {
            return TrendDirection.DOWNTREND;
        }
        // 震荡
        else {
            return TrendDirection.SIDEWAYS;
        }
    }

    /**
     * 计算移动平均线
     */
    private BigDecimal calculateMA(List<KlineData> data, int period) {
        if (data.size() < period) {
            period = data.size();
        }

        BigDecimal sum = BigDecimal.ZERO;
        List<KlineData> subset = data.subList(data.size() - period, data.size());

        for (KlineData kline : subset) {
            sum = sum.add(kline.getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    /**
     * 计算趋势强度（0-100）
     */
    private double calculateTrendStrength() {
        if (dataWindow.size() < 10) {
            return 0;
        }

        int consecutiveCount = 0;
        boolean isRising = dataWindow.get(dataWindow.size() - 1).getClose()
                .compareTo(dataWindow.get(dataWindow.size() - 2).getClose()) > 0;

        for (int i = dataWindow.size() - 1; i > 0; i--) {
            boolean currentRising = dataWindow.get(i).getClose()
                    .compareTo(dataWindow.get(i - 1).getClose()) > 0;

            if (currentRising == isRising) {
                consecutiveCount++;
            } else {
                break;
            }
        }

        return Math.min(100, consecutiveCount * 10.0);
    }

    /**
     * 检测价格突变
     */
    private boolean detectPriceSpike(KlineData current) {
        if (dataWindow.size() < 2) {
            return false;
        }

        KlineData previous = dataWindow.get(dataWindow.size() - 2);
        BigDecimal change = current.getClose().subtract(previous.getClose())
                .divide(previous.getClose(), 8, RoundingMode.HALF_UP)
                .abs();

        return change.compareTo(SPIKE_THRESHOLD) > 0;
    }

    /**
     * 检测交易量异常
     */
    private boolean detectVolumeAnomaly(KlineData current) {
        if (dataWindow.size() < 10) {
            return false;
        }

        // 计算平均交易量
        BigDecimal avgVolume = BigDecimal.ZERO;
        for (int i = 0; i < dataWindow.size() - 1; i++) {
            avgVolume = avgVolume.add(dataWindow.get(i).getVolume());
        }
        avgVolume = avgVolume.divide(BigDecimal.valueOf(dataWindow.size() - 1), 8, RoundingMode.HALF_UP);

        // 当前交易量超过平均值的3倍
        return current.getVolume().compareTo(avgVolume.multiply(BigDecimal.valueOf(3))) > 0;
    }

    /**
     * 计算波动率（标准差）
     */
    private double calculateVolatility() {
        if (dataWindow.size() < VOLATILITY_WINDOW) {
            return 0;
        }

        List<KlineData> volData = dataWindow.subList(
                dataWindow.size() - VOLATILITY_WINDOW, dataWindow.size());

        BigDecimal mean = calculateMA(volData, VOLATILITY_WINDOW);
        BigDecimal variance = BigDecimal.ZERO;

        for (KlineData kline : volData) {
            BigDecimal diff = kline.getClose().subtract(mean);
            variance = variance.add(diff.multiply(diff));
        }

        variance = variance.divide(BigDecimal.valueOf(VOLATILITY_WINDOW), 8, RoundingMode.HALF_UP);

        return Math.sqrt(variance.doubleValue());
    }

    /**
     * 评估风险等级
     */
    private RiskLevel assessRiskLevel(double volatility) {
        if (volatility < 50) {
            return RiskLevel.LOW;
        } else if (volatility < 150) {
            return RiskLevel.MEDIUM;
        } else if (volatility < 300) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.EXTREME;
        }
    }

    /**
     * 查找支撑位（最近的最低价）
     */
    private BigDecimal findSupportLevel() {
        if (dataWindow.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal support = dataWindow.get(0).getLow();
        for (KlineData kline : dataWindow) {
            if (kline.getLow().compareTo(support) < 0) {
                support = kline.getLow();
            }
        }
        return support;
    }

    /**
     * 查找压力位（最近的最高价）
     */
    private BigDecimal findResistanceLevel() {
        if (dataWindow.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal resistance = dataWindow.get(0).getHigh();
        for (KlineData kline : dataWindow) {
            if (kline.getHigh().compareTo(resistance) > 0) {
                resistance = kline.getHigh();
            }
        }
        return resistance;
    }

    /**
     * 计算动量（价格变化速度）
     */
    private double calculateMomentum() {
        if (dataWindow.size() < 10) {
            return 0;
        }

        BigDecimal current = dataWindow.getLast().getClose();
        BigDecimal previous = dataWindow.get(dataWindow.size() - 10).getClose();

        return current.subtract(previous)
                .divide(previous, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    /**
     * 计算MACD指标
     */
    public TechnicalIndicators.MACDResult calculateMACDIndicator() {
        if (dataWindow.size() < 34) {
            return new TechnicalIndicators.MACDResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "HOLD");
        }
        return TechnicalIndicators.calculateMACD(new ArrayList<>(dataWindow));
    }

    /**
     * 计算布林带
     */
    public TechnicalIndicators.BollingerBandsResult calculateBollingerBands() {
        if (dataWindow.isEmpty()) {
            return new TechnicalIndicators.BollingerBandsResult(
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, "IN");
        }
        BigDecimal currentPrice = dataWindow.getLast().getClose();
        return TechnicalIndicators.calculateBollingerBands(
                new ArrayList<>(dataWindow), 20, 2.0, currentPrice);
    }

    /**
     * 计算KDJ指标
     */
    public TechnicalIndicators.KDJResult calculateKDJIndicator() {
        if (dataWindow.size() < 9) {
            return new TechnicalIndicators.KDJResult(50, 50, 50, "NEUTRAL");
        }
        return TechnicalIndicators.calculateKDJ(new ArrayList<>(dataWindow), 9);
    }

    /**
     * 计算VWAP
     */
    public BigDecimal calculateVWAP() {
        return TechnicalIndicators.calculateVWAP(new ArrayList<>(dataWindow));
    }

    /**
     * 计算ATR
     */
    public BigDecimal calculateATR() {
        if (dataWindow.size() < 15) {
            return BigDecimal.ZERO;
        }
        return TechnicalIndicators.calculateATR(new ArrayList<>(dataWindow), 14);
    }

    /**
     * 计算RSI（相对强弱指标）
     */
    private double calculateRSI() {
        int period = 14;
        if (dataWindow.size() < period + 1) {
            return 50; // 中性值
        }

        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        for (int i = dataWindow.size() - period; i < dataWindow.size(); i++) {
            BigDecimal change = dataWindow.get(i).getClose()
                    .subtract(dataWindow.get(i - 1).getClose());

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }

        if (losses.compareTo(BigDecimal.ZERO) == 0) {
            return 100;
        }

        BigDecimal avgGain = gains.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);

        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP))
                .doubleValue();
    }

    /**
     * 趋势方向
     */
    public enum TrendDirection {
        UPTREND("上涨"),
        DOWNTREND("下跌"),
        SIDEWAYS("震荡"),
        UNKNOWN("未知");

        private final String description;

        TrendDirection(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 风险等级
     */
    public enum RiskLevel {
        LOW("低风险"),
        MEDIUM("中等风险"),
        HIGH("高风险"),
        EXTREME("极高风险");

        private final String description;

        RiskLevel(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 分析结果
     */
    @Data
    public static class AnalysisResult {
        private String symbol;
        private BigDecimal currentPrice;
        private String timestamp;

        // 趋势分析
        private TrendDirection trend;
        private double trendStrength; // 0-100

        // 异常检测
        private boolean priceSpike;
        private boolean volumeAnomaly;

        // 风险评估
        private double volatility;
        private RiskLevel riskLevel;

        // 技术指标
        private BigDecimal supportLevel;
        private BigDecimal resistanceLevel;
        private double momentum;
        private double rsi; // 0-100

        public static AnalysisResult insufficient(String symbol) {
            AnalysisResult result = new AnalysisResult();
            result.setSymbol(symbol);
            result.setTrend(TrendDirection.UNKNOWN);
            result.setRiskLevel(RiskLevel.LOW);
            result.setSupportLevel(BigDecimal.ZERO);
            result.setResistanceLevel(BigDecimal.ZERO);
            result.setCurrentPrice(BigDecimal.ZERO);
            return result;
        }

        /**
         * 生成分析报告
         */
        public String generateReport() {
            StringBuilder report = new StringBuilder();
            report.append(String.format("【%s 实时分析报告】\n", symbol));
            report.append(String.format("当前价格: %s\n", currentPrice));
            report.append(String.format("趋势: %s (强度: %.1f%%)\n", trend.getDescription(), trendStrength));
            report.append(String.format("RSI: %.2f %s\n", rsi, getRSISignal()));
            report.append(String.format("波动率: %.2f (%s)\n", volatility, riskLevel.getDescription()));
            report.append(String.format("支撑位: %s | 压力位: %s\n", supportLevel, resistanceLevel));

            if (priceSpike) {
                report.append("⚠️ 检测到价格突变！\n");
            }
            if (volumeAnomaly) {
                report.append("⚠️ 检测到交易量异常！\n");
            }

            return report.toString();
        }

        private String getRSISignal() {
            if (rsi > 70)
                return "(超买)";
            if (rsi < 30)
                return "(超卖)";
            return "(正常)";
        }
    }
}
