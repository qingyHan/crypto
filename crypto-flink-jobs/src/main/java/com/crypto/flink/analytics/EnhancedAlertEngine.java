package com.crypto.flink.analytics;

import com.crypto.flink.analytics.RealTimeAnalyzer.AnalysisResult;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 增强预警引擎
 * <p>
 * 核心特性：
 * 1. 多维度检测：价格、交易量、波动率、趋势反转、技术指标
 * 2. 动态阈值：基于历史波动率自适应调整
 * 3. 预警评分：综合严重程度、置信度、紧急度
 * 4. 预警聚合：避免重复预警
 * 5. 智能降噪：过滤误报
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class EnhancedAlertEngine {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 动态阈值配置 (使用ConcurrentHashMap保证线程安全)
    private final Map<String, ThresholdConfig> thresholdConfigs = new ConcurrentHashMap<>();

    // 预警历史（用于去重，使用ConcurrentHashMap保证线程安全）
    private final Map<String, LocalDateTime> recentAlerts = new ConcurrentHashMap<>();
    private static final int ALERT_COOLDOWN_MINUTES = 5;

    /**
     * 分析并生成预警
     */
    public List<Alert> analyzeAndAlert(AnalysisResult analysis) {
        List<Alert> alerts = new ArrayList<>();
        String symbol = analysis.getSymbol();

        // 更新动态阈值
        updateThresholds(symbol, analysis);

        // 1. 价格突变预警
        if (analysis.isPriceSpike()) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.PRICE_SPIKE,
                    AlertSeverity.HIGH,
                    String.format("价格突变：当前价格 %s，建议关注", analysis.getCurrentPrice()),
                    0.85);
            if (alert != null)
                alerts.add(alert);
        }

        // 2. 交易量异常预警
        if (analysis.isVolumeAnomaly()) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.VOLUME_ANOMALY,
                    AlertSeverity.MEDIUM,
                    "交易量异常：交易量突增3倍以上",
                    0.75);
            if (alert != null)
                alerts.add(alert);
        }

        // 3. 波动率预警
        if (analysis.getRiskLevel() == RealTimeAnalyzer.RiskLevel.EXTREME) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.VOLATILITY_SURGE,
                    AlertSeverity.CRITICAL,
                    String.format("极高波动率：%.2f，市场极不稳定", analysis.getVolatility()),
                    0.90);
            if (alert != null)
                alerts.add(alert);
        } else if (analysis.getRiskLevel() == RealTimeAnalyzer.RiskLevel.HIGH) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.VOLATILITY_SURGE,
                    AlertSeverity.MEDIUM,
                    String.format("高波动率：%.2f，注意风险", analysis.getVolatility()),
                    0.70);
            if (alert != null)
                alerts.add(alert);
        }

        // 4. 趋势反转预警
        if (detectTrendReversal(symbol, analysis)) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.TREND_REVERSAL,
                    AlertSeverity.HIGH,
                    String.format("趋势反转：%s (强度: %.1f%%)",
                            analysis.getTrend().getDescription(), analysis.getTrendStrength()),
                    0.80);
            if (alert != null)
                alerts.add(alert);
        }

        // 5. RSI超买/超卖预警
        if (analysis.getRsi() > 70) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.RSI_OVERBOUGHT,
                    AlertSeverity.MEDIUM,
                    String.format("RSI超买：%.2f，可能回调", analysis.getRsi()),
                    0.65);
            if (alert != null)
                alerts.add(alert);
        } else if (analysis.getRsi() < 30) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.RSI_OVERSOLD,
                    AlertSeverity.MEDIUM,
                    String.format("RSI超卖：%.2f，可能反弹", analysis.getRsi()),
                    0.65);
            if (alert != null)
                alerts.add(alert);
        }

        // 6. 支撑位/压力位突破预警
        if (detectSupportBreak(analysis)) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.SUPPORT_BREAK,
                    AlertSeverity.HIGH,
                    String.format("跌破支撑位：%s < %s",
                            analysis.getCurrentPrice(), analysis.getSupportLevel()),
                    0.75);
            if (alert != null)
                alerts.add(alert);
        }

        if (detectResistanceBreak(analysis)) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.RESISTANCE_BREAK,
                    AlertSeverity.MEDIUM,
                    String.format("突破压力位：%s > %s",
                            analysis.getCurrentPrice(), analysis.getResistanceLevel()),
                    0.70);
            if (alert != null)
                alerts.add(alert);
        }

        // 7. 动量预警
        if (Math.abs(analysis.getMomentum()) > 5) {
            Alert alert = createAlert(
                    symbol,
                    AlertType.MOMENTUM_SHIFT,
                    analysis.getMomentum() > 0 ? AlertSeverity.MEDIUM : AlertSeverity.HIGH,
                    String.format("强劲动量：%.2f%%，市场活跃", analysis.getMomentum()),
                    0.60);
            if (alert != null)
                alerts.add(alert);
        }

        return alerts;
    }

    /**
     * 创建预警（包含去重逻辑）
     */
    private Alert createAlert(String symbol, AlertType type, AlertSeverity severity,
            String message, double confidence) {
        String alertKey = symbol + "_" + type;
        LocalDateTime now = LocalDateTime.now();

        // 检查是否在冷却期内
        if (recentAlerts.containsKey(alertKey)) {
            LocalDateTime lastAlert = recentAlerts.get(alertKey);
            if (now.minusMinutes(ALERT_COOLDOWN_MINUTES).isBefore(lastAlert)) {
                log.debug("预警冷却中，跳过: {}", alertKey);
                return null;
            }
        }

        // 记录新预警
        recentAlerts.put(alertKey, now);

        Alert alert = new Alert();
        alert.setSymbol(symbol);
        alert.setType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setConfidence(confidence);
        alert.setTimestamp(now.format(FORMATTER));
        alert.setScore(calculateScore(severity, confidence));

        log.info("生成预警: {} - {} - {}", symbol, type, message);
        return alert;
    }

    /**
     * 计算预警评分
     */
    private int calculateScore(AlertSeverity severity, double confidence) {
        int severityScore = switch (severity) {
            case CRITICAL -> 100;
            case HIGH -> 75;
            case MEDIUM -> 50;
            case LOW -> 25;
        };

        return (int) (severityScore * confidence);
    }

    /**
     * 检测趋势反转
     */
    private boolean detectTrendReversal(String symbol, AnalysisResult current) {
        ThresholdConfig config = thresholdConfigs.get(symbol);
        if (config == null) {
            return false;
        }

        RealTimeAnalyzer.TrendDirection prevTrend = config.getLastTrend();
        RealTimeAnalyzer.TrendDirection currTrend = current.getTrend();

        // 趋势反转：上涨→下跌 或 下跌→上涨
        boolean reversed = (prevTrend == RealTimeAnalyzer.TrendDirection.UPTREND &&
                currTrend == RealTimeAnalyzer.TrendDirection.DOWNTREND) ||
                (prevTrend == RealTimeAnalyzer.TrendDirection.DOWNTREND &&
                        currTrend == RealTimeAnalyzer.TrendDirection.UPTREND);

        config.setLastTrend(currTrend);
        return reversed && current.getTrendStrength() > 50;
    }

    /**
     * 检测支撑位突破
     */
    private boolean detectSupportBreak(AnalysisResult analysis) {
        if (analysis.getSupportLevel() == null ||
                analysis.getSupportLevel().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        return analysis.getCurrentPrice().compareTo(
                analysis.getSupportLevel().multiply(new BigDecimal("0.995"))) < 0;
    }

    /**
     * 检测压力位突破
     */
    private boolean detectResistanceBreak(AnalysisResult analysis) {
        if (analysis.getResistanceLevel() == null ||
                analysis.getResistanceLevel().compareTo(BigDecimal.ZERO) == 0) {
            return false;
        }

        return analysis.getCurrentPrice().compareTo(
                analysis.getResistanceLevel().multiply(new BigDecimal("1.005"))) > 0;
    }

    /**
     * 更新动态阈值
     */
    private void updateThresholds(String symbol, AnalysisResult analysis) {
        ThresholdConfig config = thresholdConfigs.computeIfAbsent(
                symbol, k -> new ThresholdConfig());

        config.updateVolatility(analysis.getVolatility());
        config.setLastTrend(analysis.getTrend());
    }

    /**
     * 预警类型
     */
    @Getter
    public enum AlertType {
        PRICE_SPIKE("价格突变"),
        PRICE_DROP("价格暴跌"),
        VOLUME_ANOMALY("交易量异常"),
        VOLATILITY_SURGE("波动率激增"),
        TREND_REVERSAL("趋势反转"),
        SUPPORT_BREAK("跌破支撑"),
        RESISTANCE_BREAK("突破压力"),
        RSI_OVERBOUGHT("RSI超买"),
        RSI_OVERSOLD("RSI超卖"),
        MOMENTUM_SHIFT("动量变化");

        private final String description;

        AlertType(String description) {
            this.description = description;
        }

    }

    /**
     * 预警严重程度
     */
    @Getter
    public enum AlertSeverity {
        CRITICAL("紧急", "#FF0000"),
        HIGH("重要", "#FF6600"),
        MEDIUM("中等", "#FFA500"),
        LOW("提示", "#FFD700");

        private final String label;
        private final String color;

        AlertSeverity(String label, String color) {
            this.label = label;
            this.color = color;
        }

    }

    /**
     * 预警对象
     */
    @Data
    public static class Alert {
        private String symbol;
        private AlertType type;
        private AlertSeverity severity;
        private String message;
        private double confidence; // 0-1
        private int score; // 0-100
        private String timestamp;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", symbol);
            map.put("type", type.name());
            map.put("typeDesc", type.getDescription());
            map.put("severity", severity.name());
            map.put("severityLabel", severity.getLabel());
            map.put("severityColor", severity.getColor());
            map.put("message", message);
            map.put("confidence", confidence);
            map.put("score", score);
            map.put("timestamp", timestamp);
            return map;
        }
    }

    /**
     * 动态阈值配置
     */
    @Data
    private static class ThresholdConfig {
        private double avgVolatility;
        private int volatilitySamples = 0;
        private RealTimeAnalyzer.TrendDirection lastTrend = RealTimeAnalyzer.TrendDirection.UNKNOWN;

        public void updateVolatility(double newVolatility) {
            avgVolatility = (avgVolatility * volatilitySamples + newVolatility) / (volatilitySamples + 1);
            volatilitySamples++;
        }
    }
}
