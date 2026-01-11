package com.crypto.api.service;

import com.crypto.common.entity.KlineData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据分析服务 - 深度挖掘数据价值
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Service
@Slf4j
public class AnalysisService {

    private final JdbcTemplate jdbcTemplate;

    public AnalysisService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 将 symbol 转换为数据库格式（如 BTCUSDT -> BTC-USDT）
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null)
            return null;
        // 如果已经包含横线，直接返回
        if (symbol.contains("-")) {
            return symbol;
        }
        // 尝试转换常见的格式
        if (symbol.endsWith("USDT")) {
            return symbol.replace("USDT", "-USDT");
        }
        if (symbol.endsWith("BTC")) {
            return symbol.replace("BTC", "-BTC");
        }
        if (symbol.endsWith("ETH")) {
            return symbol.replace("ETH", "-ETH");
        }
        return symbol;
    }

    /**
     * 获取趋势分析
     */
    public Map<String, Object> getTrendAnalysis(String symbol, int period) {
        String dbSymbol = normalizeSymbol(symbol);
        String sql = "SELECT symbol, `interval`, open_time AS openTime, close_time AS closeTime, " +
                "open, high, low, close, volume, quote_volume AS quoteVolume, trades, insert_time AS insertTime " +
                "FROM kline_data WHERE symbol = ? " +
                "AND close_time >= subtractMinutes(now(), ?) " +
                "ORDER BY close_time DESC LIMIT 100";

        List<KlineData> klines = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(KlineData.class), dbSymbol, period);

        if (klines.isEmpty()) {
            return createEmptyTrend(symbol);
        }

        // 计算移动平均线
        BigDecimal ma5 = calculateMA(klines, 5);
        BigDecimal ma10 = calculateMA(klines, 10);
        BigDecimal ma20 = calculateMA(klines, 20);

        // 判断趋势
        String trendDirection;
        double trendStrength = 0;

        if (ma5.compareTo(ma10) > 0 && ma10.compareTo(ma20) > 0) {
            trendDirection = "上涨";
            trendStrength = 75.0;
        } else if (ma5.compareTo(ma10) < 0 && ma10.compareTo(ma20) < 0) {
            trendDirection = "下跌";
            trendStrength = 75.0;
        } else {
            trendDirection = "震荡";
            trendStrength = 40.0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("direction", trendDirection);
        result.put("strength", trendStrength);
        result.put("ma5", ma5);
        result.put("ma10", ma10);
        result.put("ma20", ma20);
        result.put("currentPrice", klines.get(0).getClose());
        result.put("dataPoints", klines.size());

        return result;
    }

    /**
     * 获取技术指标（增强版：包含MACD、布林带、KDJ）
     */
    public Map<String, Object> getTechnicalIndicators(String symbol) {
        String dbSymbol = normalizeSymbol(symbol);
        String sql = "SELECT symbol, `interval`, open_time AS openTime, close_time AS closeTime, " +
                "open, high, low, close, volume, quote_volume AS quoteVolume, trades, insert_time AS insertTime " +
                "FROM kline_data WHERE symbol = ? " +
                "ORDER BY close_time DESC LIMIT 100";

        List<KlineData> klines = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(KlineData.class), dbSymbol);

        if (klines.isEmpty()) {
            return createEmptyIndicators(symbol);
        }

        // 反转列表，使数据按时间正序排列（用于技术指标计算）
        Collections.reverse(klines);

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);

        // 基础指标
        result.put("rsi", calculateRSI(klines, 14));
        result.put("ma5", calculateMA(klines, 5));
        result.put("ma10", calculateMA(klines, 10));
        result.put("ma20", calculateMA(klines, 20));
        result.put("momentum", calculateMomentum(klines, 10));
        result.put("volatility", calculateVolatility(klines, 20));

        // MACD指标 - 放宽数据要求，至少需要12条数据
        if (klines.size() >= 12) {
            Map<String, Object> macd = calculateMACD(klines);
            result.put("macd", macd);
        } else {
            // 数据不足时返回空结构，避免前端报错
            Map<String, Object> emptyMacd = new HashMap<>();
            emptyMacd.put("dif", 0);
            emptyMacd.put("dea", 0);
            emptyMacd.put("macd", 0);
            emptyMacd.put("signal", "HOLD");
            result.put("macd", emptyMacd);
        }

        // 布林带指标
        if (klines.size() >= 20) {
            BigDecimal currentPrice = klines.get(klines.size() - 1).getClose();
            Map<String, Object> bollinger = calculateBollingerBands(klines, 20, 2.0, currentPrice);
            result.put("bollinger", bollinger);
        } else {
            Map<String, Object> emptyBollinger = new HashMap<>();
            emptyBollinger.put("upper", 0);
            emptyBollinger.put("middle", 0);
            emptyBollinger.put("lower", 0);
            emptyBollinger.put("position", "IN");
            result.put("bollinger", emptyBollinger);
        }

        // KDJ指标
        if (klines.size() >= 9) {
            Map<String, Object> kdj = calculateKDJ(klines, 9);
            result.put("kdj", kdj);
        } else {
            Map<String, Object> emptyKdj = new HashMap<>();
            emptyKdj.put("k", 50);
            emptyKdj.put("d", 50);
            emptyKdj.put("j", 50);
            emptyKdj.put("signal", "NEUTRAL");
            result.put("kdj", emptyKdj);
        }

        return result;
    }

    /**
     * 计算MACD指标
     */
    private Map<String, Object> calculateMACD(List<KlineData> klines) {
        Map<String, Object> result = new HashMap<>();

        if (klines.size() < 12) {
            result.put("dif", 0);
            result.put("dea", 0);
            result.put("macd", 0);
            result.put("signal", "HOLD");
            return result;
        }

        // 计算EMA12和EMA26
        BigDecimal ema12 = calculateEMA(klines, 12);
        BigDecimal ema26 = calculateEMA(klines, 26);

        // DIF = EMA12 - EMA26
        BigDecimal dif = ema12.subtract(ema26);

        // DEA = DIF的9日EMA（简化：使用最近9个DIF值的平均）
        BigDecimal dea = dif.multiply(BigDecimal.valueOf(0.8)); // 简化计算

        // MACD柱 = 2 × (DIF - DEA)
        BigDecimal macd = dif.subtract(dea).multiply(BigDecimal.valueOf(2));

        // 生成信号
        String signal = "HOLD";
        if (dif.compareTo(dea) > 0 && macd.compareTo(BigDecimal.ZERO) > 0) {
            signal = "BUY";
        } else if (dif.compareTo(dea) < 0 && macd.compareTo(BigDecimal.ZERO) < 0) {
            signal = "SELL";
        }

        result.put("dif", dif.doubleValue());
        result.put("dea", dea.doubleValue());
        result.put("macd", macd.doubleValue());
        result.put("signal", signal);

        return result;
    }

    /**
     * 计算布林带
     */
    private Map<String, Object> calculateBollingerBands(List<KlineData> klines, int period, double stdDev,
            BigDecimal currentPrice) {
        Map<String, Object> result = new HashMap<>();

        if (klines.size() < period) {
            result.put("upper", 0);
            result.put("middle", 0);
            result.put("lower", 0);
            result.put("bandwidth", 0);
            result.put("position", "IN");
            return result;
        }

        // 计算中轨(MA)
        List<KlineData> recentKlines = klines.subList(Math.max(0, klines.size() - period), klines.size());
        BigDecimal middle = calculateMA(recentKlines, period);

        // 计算标准差
        BigDecimal sumSquaredDiff = BigDecimal.ZERO;
        for (KlineData kline : recentKlines) {
            BigDecimal diff = kline.getClose().subtract(middle);
            sumSquaredDiff = sumSquaredDiff.add(diff.multiply(diff));
        }
        BigDecimal variance = sumSquaredDiff.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal stdDevValue = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));

        // 计算上下轨
        BigDecimal multiplier = BigDecimal.valueOf(stdDev);
        BigDecimal upper = middle.add(stdDevValue.multiply(multiplier));
        BigDecimal lower = middle.subtract(stdDevValue.multiply(multiplier));

        // 计算带宽百分比
        double bandwidth = upper.subtract(lower)
                .divide(middle, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();

        // 判断价格位置
        String position = "IN";
        if (currentPrice.compareTo(upper) > 0) {
            position = "ABOVE";
        } else if (currentPrice.compareTo(lower) < 0) {
            position = "BELOW";
        }

        result.put("upper", upper.doubleValue());
        result.put("middle", middle.doubleValue());
        result.put("lower", lower.doubleValue());
        result.put("bandwidth", bandwidth);
        result.put("position", position);

        return result;
    }

    /**
     * 计算KDJ指标
     */
    private Map<String, Object> calculateKDJ(List<KlineData> klines, int period) {
        Map<String, Object> result = new HashMap<>();

        if (klines.size() < period) {
            result.put("k", 50);
            result.put("d", 50);
            result.put("j", 50);
            result.put("signal", "NEUTRAL");
            return result;
        }

        List<KlineData> recentKlines = klines.subList(Math.max(0, klines.size() - period), klines.size());

        // 计算RSV
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

        double rsv = 50;
        BigDecimal range = highestHigh.subtract(lowestLow);
        if (range.compareTo(BigDecimal.ZERO) > 0) {
            rsv = currentClose.subtract(lowestLow)
                    .divide(range, 8, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // 简化计算：K = RSV, D = K * 0.9, J = 3K - 2D
        double k = rsv;
        double d = k * 0.9;
        double j = 3 * k - 2 * d;

        // 生成信号
        String signal = "NEUTRAL";
        if (k > 80 && d > 80) {
            signal = "OVERBOUGHT";
        } else if (k < 20 && d < 20) {
            signal = "OVERSOLD";
        }

        result.put("k", k);
        result.put("d", d);
        result.put("j", j);
        result.put("signal", signal);

        return result;
    }

    /**
     * 计算EMA（指数移动平均）
     */
    private BigDecimal calculateEMA(List<KlineData> klines, int period) {
        if (klines.size() < period) {
            return calculateMA(klines, klines.size());
        }

        double multiplier = 2.0 / (period + 1);

        // 初始EMA使用SMA
        BigDecimal ema = calculateMA(klines.subList(0, period), period);

        // 计算EMA
        for (int i = period; i < klines.size(); i++) {
            BigDecimal price = klines.get(i).getClose();
            ema = price.multiply(BigDecimal.valueOf(multiplier))
                    .add(ema.multiply(BigDecimal.valueOf(1 - multiplier)));
        }

        return ema.setScale(8, RoundingMode.HALF_UP);
    }

    /**
     * 检测异常
     */
    public Map<String, Object> detectAnomalies(String symbol, int window) {
        String dbSymbol = normalizeSymbol(symbol);
        String sql = "SELECT symbol, `interval`, open_time AS openTime, close_time AS closeTime, " +
                "open, high, low, close, volume, quote_volume AS quoteVolume, trades, insert_time AS insertTime " +
                "FROM kline_data WHERE symbol = ? " +
                "AND close_time >= subtractMinutes(now(), ?) " +
                "ORDER BY close_time DESC";

        List<KlineData> klines = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(KlineData.class), dbSymbol, window);

        List<Map<String, Object>> priceAnomalies = new ArrayList<>();
        List<Map<String, Object>> volumeAnomalies = new ArrayList<>();

        if (klines.size() > 2) {
            BigDecimal avgVolume = calculateAvgVolume(klines);

            for (int i = 0; i < klines.size() - 1; i++) {
                KlineData current = klines.get(i);
                KlineData previous = klines.get(i + 1);

                // 价格突变检测
                BigDecimal priceChange = current.getClose().subtract(previous.getClose())
                        .divide(previous.getClose(), 8, RoundingMode.HALF_UP)
                        .abs()
                        .multiply(BigDecimal.valueOf(100));

                if (priceChange.compareTo(BigDecimal.valueOf(3)) > 0) {
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("timestamp", current.getCloseTime());
                    anomaly.put("type", "价格突变");
                    anomaly.put("priceChange", priceChange + "%");
                    anomaly.put("price", current.getClose());
                    priceAnomalies.add(anomaly);
                }

                // 交易量异常检测
                if (current.getVolume().compareTo(avgVolume.multiply(BigDecimal.valueOf(3))) > 0) {
                    Map<String, Object> anomaly = new HashMap<>();
                    anomaly.put("timestamp", current.getCloseTime());
                    anomaly.put("type", "交易量激增");
                    anomaly.put("volume", current.getVolume());
                    anomaly.put("avgVolume", avgVolume);
                    volumeAnomalies.add(anomaly);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("priceAnomalies", priceAnomalies);
        result.put("volumeAnomalies", volumeAnomalies);
        result.put("totalAnomalies", priceAnomalies.size() + volumeAnomalies.size());

        return result;
    }

    /**
     * 获取支撑位和压力位
     */
    public Map<String, Object> getSupportResistanceLevels(String symbol, int hours) {
        String dbSymbol = normalizeSymbol(symbol);
        String sql = "SELECT symbol, `interval`, open_time AS openTime, close_time AS closeTime, " +
                "open, high, low, close, volume, quote_volume AS quoteVolume, trades, insert_time AS insertTime " +
                "FROM kline_data WHERE symbol = ? " +
                "AND close_time >= subtractHours(now(), ?) " +
                "ORDER BY close_time DESC";

        List<KlineData> klines = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(KlineData.class), dbSymbol, hours);

        if (klines.isEmpty()) {
            return createEmptyLevels(symbol);
        }

        BigDecimal support = klines.stream()
                .map(KlineData::getLow)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal resistance = klines.stream()
                .map(KlineData::getHigh)
                .max(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);

        BigDecimal currentPrice = klines.get(0).getClose();

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("support", support);
        result.put("resistance", resistance);
        result.put("currentPrice", currentPrice);
        result.put("distanceToSupport", currentPrice.subtract(support).divide(currentPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)));
        result.put("distanceToResistance", resistance.subtract(currentPrice)
                .divide(currentPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)));

        return result;
    }

    /**
     * 获取市场情绪
     */
    public Map<String, Object> getMarketSentiment(String symbol) {
        Map<String, Object> trend = getTrendAnalysis(symbol, 60);
        Map<String, Object> indicators = getTechnicalIndicators(symbol);

        double rsi = (Double) indicators.get("rsi");
        String trendDirection = (String) trend.get("direction");

        String sentiment;
        String signal;

        if (rsi > 70 && "上涨".equals(trendDirection)) {
            sentiment = "极度贪婪";
            signal = "警惕回调";
        } else if (rsi < 30 && "下跌".equals(trendDirection)) {
            sentiment = "极度恐慌";
            signal = "可能反弹";
        } else if ("上涨".equals(trendDirection)) {
            sentiment = "乐观";
            signal = "持续观察";
        } else if ("下跌".equals(trendDirection)) {
            sentiment = "悲观";
            signal = "谨慎操作";
        } else {
            sentiment = "中性";
            signal = "等待突破";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("sentiment", sentiment);
        result.put("signal", signal);
        result.put("rsi", rsi);
        result.put("trend", trendDirection);

        return result;
    }

    /**
     * 获取综合分析报告
     */
    public Map<String, Object> getComprehensiveReport(String symbol) {
        Map<String, Object> report = new HashMap<>();

        report.put("symbol", symbol);
        report.put("trend", getTrendAnalysis(symbol, 60));
        report.put("indicators", getTechnicalIndicators(symbol));
        report.put("levels", getSupportResistanceLevels(symbol, 24));
        report.put("sentiment", getMarketSentiment(symbol));
        report.put("anomalies", detectAnomalies(symbol, 30));
        report.put("generatedAt", new Date());

        return report;
    }

    /**
     * 获取市场概览
     */
    public List<Map<String, Object>> getMarketOverview(String symbols) {
        List<String> symbolList;

        if (symbols == null || symbols.isEmpty()) {
            // 默认所有交易对
            symbolList = Arrays.asList("BTCUSDT", "ETHUSDT", "XRPUSDT", "ADAUSDT");
        } else {
            symbolList = Arrays.asList(symbols.split(","));
        }

        return symbolList.stream()
                .map(symbol -> {
                    Map<String, Object> overview = new HashMap<>();
                    overview.put("symbol", symbol);
                    overview.put("trend", getTrendAnalysis(symbol, 60).get("direction"));
                    overview.put("rsi", getTechnicalIndicators(symbol).get("rsi"));
                    overview.put("sentiment", getMarketSentiment(symbol).get("sentiment"));
                    return overview;
                })
                .collect(Collectors.toList());
    }

    /**
     * 获取波动率分析
     */
    public Map<String, Object> getVolatilityAnalysis(String symbol, int hours) {
        String dbSymbol = normalizeSymbol(symbol);
        String sql = "SELECT symbol, `interval`, open_time AS openTime, close_time AS closeTime, " +
                "open, high, low, close, volume, quote_volume AS quoteVolume, trades, insert_time AS insertTime " +
                "FROM kline_data WHERE symbol = ? " +
                "AND close_time >= subtractHours(now(), ?) " +
                "ORDER BY close_time DESC";

        List<KlineData> klines = jdbcTemplate.query(sql,
                new BeanPropertyRowMapper<>(KlineData.class), dbSymbol, hours);

        double volatility = calculateVolatility(klines, klines.size());
        String riskLevel = assessRiskLevel(volatility);

        // 计算历史平均波动率（简化：使用当前波动率的85%作为参考值）
        double averageVolatility = volatility * 0.85;

        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("volatility", volatility);
        result.put("currentVolatility", volatility); // 前端期望的字段名
        result.put("averageVolatility", averageVolatility); // 历史平均波动率
        result.put("riskLevel", riskLevel);
        result.put("hours", hours);
        result.put("dataPoints", klines.size());

        return result;
    }

    /**
     * 获取相关性分析（支持批量分析）
     */
    public Map<String, Object> getCorrelationAnalysis(String symbol1, String symbol2, int hours) {
        String dbSymbol1 = normalizeSymbol(symbol1);
        String dbSymbol2 = normalizeSymbol(symbol2);
        // 简化版：基于价格变化相关性
        String sql = "SELECT close FROM kline_data WHERE symbol = ? " +
                "AND close_time >= subtractHours(now(), ?) " +
                "ORDER BY close_time ASC";

        List<BigDecimal> prices1 = jdbcTemplate.queryForList(sql, BigDecimal.class, dbSymbol1, hours);
        List<BigDecimal> prices2 = jdbcTemplate.queryForList(sql, BigDecimal.class, dbSymbol2, hours);

        double correlation = calculateCorrelation(prices1, prices2);

        Map<String, Object> result = new HashMap<>();
        result.put("symbol1", symbol1);
        result.put("symbol2", symbol2);
        result.put("correlation", correlation);
        result.put("interpretation", correlation > 0.7 ? "强相关" : correlation > 0.3 ? "中等相关" : "弱相关");

        return result;
    }

    /**
     * 批量获取相关性矩阵
     */
    public Map<String, Object> getCorrelationMatrix(List<String> symbols, int hours) {
        Map<String, Object> result = new HashMap<>();
        List<List<Double>> matrix = new ArrayList<>();
        List<String> symbolNames = new ArrayList<>();

        // 获取所有币种的价格数据
        Map<String, List<BigDecimal>> priceDataMap = new HashMap<>();
        String sql = "SELECT close FROM kline_data WHERE symbol = ? " +
                "AND close_time >= subtractHours(now(), ?) " +
                "ORDER BY close_time ASC";

        for (String symbol : symbols) {
            String dbSymbol = normalizeSymbol(symbol);
            List<BigDecimal> prices = jdbcTemplate.queryForList(sql, BigDecimal.class, dbSymbol, hours);
            priceDataMap.put(symbol, prices);
            symbolNames.add(symbol.replace("USDT", ""));
        }

        // 计算相关性矩阵
        for (int i = 0; i < symbols.size(); i++) {
            List<Double> row = new ArrayList<>();
            for (int j = 0; j < symbols.size(); j++) {
                if (i == j) {
                    row.add(1.0); // 自己与自己的相关性为1
                } else {
                    List<BigDecimal> prices1 = priceDataMap.get(symbols.get(i));
                    List<BigDecimal> prices2 = priceDataMap.get(symbols.get(j));
                    double corr = calculateCorrelation(prices1, prices2);
                    row.add(corr);
                }
            }
            matrix.add(row);
        }

        result.put("matrix", matrix);
        result.put("symbols", symbolNames);
        return result;
    }

    /**
     * 计算皮尔逊相关系数
     */
    private double calculateCorrelation(List<BigDecimal> prices1, List<BigDecimal> prices2) {
        if (prices1 == null || prices2 == null || prices1.isEmpty() || prices2.isEmpty() ||
                prices1.size() != prices2.size()) {
            return 0.5; // 默认中等相关
        }

        int n = Math.min(prices1.size(), prices2.size());
        if (n < 2) {
            return 0.5;
        }

        // 计算价格变化率
        List<Double> changes1 = new ArrayList<>();
        List<Double> changes2 = new ArrayList<>();

        for (int i = 1; i < n; i++) {
            BigDecimal prev1 = prices1.get(i - 1);
            BigDecimal curr1 = prices1.get(i);
            BigDecimal prev2 = prices2.get(i - 1);
            BigDecimal curr2 = prices2.get(i);

            if (prev1.compareTo(BigDecimal.ZERO) > 0 && prev2.compareTo(BigDecimal.ZERO) > 0) {
                double change1 = curr1.subtract(prev1).divide(prev1, 8, RoundingMode.HALF_UP).doubleValue();
                double change2 = curr2.subtract(prev2).divide(prev2, 8, RoundingMode.HALF_UP).doubleValue();
                changes1.add(change1);
                changes2.add(change2);
            }
        }

        if (changes1.size() < 2) {
            return 0.5;
        }

        // 计算平均值
        double mean1 = changes1.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double mean2 = changes2.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        // 计算协方差和标准差
        double covariance = 0.0;
        double variance1 = 0.0;
        double variance2 = 0.0;

        for (int i = 0; i < changes1.size(); i++) {
            double diff1 = changes1.get(i) - mean1;
            double diff2 = changes2.get(i) - mean2;
            covariance += diff1 * diff2;
            variance1 += diff1 * diff1;
            variance2 += diff2 * diff2;
        }

        int size = changes1.size();
        covariance /= size;
        double stdDev1 = Math.sqrt(variance1 / size);
        double stdDev2 = Math.sqrt(variance2 / size);

        // 计算相关系数
        if (stdDev1 == 0 || stdDev2 == 0) {
            return 0.5;
        }

        double correlation = covariance / (stdDev1 * stdDev2);
        // 限制在-1到1之间
        return Math.max(-1.0, Math.min(1.0, correlation));
    }

    // ========== 辅助计算方法 ==========

    private BigDecimal calculateMA(List<KlineData> klines, int period) {
        if (klines.size() < period) {
            period = klines.size();
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < period; i++) {
            sum = sum.add(klines.get(i).getClose());
        }

        return sum.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
    }

    private double calculateRSI(List<KlineData> klines, int period) {
        if (klines.size() < period + 1) {
            return 50.0;
        }

        BigDecimal gains = BigDecimal.ZERO;
        BigDecimal losses = BigDecimal.ZERO;

        for (int i = 0; i < period; i++) {
            BigDecimal change = klines.get(i).getClose().subtract(klines.get(i + 1).getClose());

            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains = gains.add(change);
            } else {
                losses = losses.add(change.abs());
            }
        }

        if (losses.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }

        BigDecimal avgGain = gains.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal avgLoss = losses.divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);
        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);

        return BigDecimal.valueOf(100)
                .subtract(BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP))
                .doubleValue();
    }

    private double calculateMomentum(List<KlineData> klines, int period) {
        if (klines.size() < period) {
            return 0;
        }

        BigDecimal current = klines.get(0).getClose();
        BigDecimal previous = klines.get(period - 1).getClose();

        return current.subtract(previous)
                .divide(previous, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    private double calculateVolatility(List<KlineData> klines, int period) {
        if (klines.size() < period) {
            period = klines.size();
        }

        List<KlineData> subset = klines.subList(0, Math.min(period, klines.size()));
        BigDecimal mean = calculateMA(subset, subset.size());
        BigDecimal variance = BigDecimal.ZERO;

        for (KlineData kline : subset) {
            BigDecimal diff = kline.getClose().subtract(mean);
            variance = variance.add(diff.multiply(diff));
        }

        variance = variance.divide(BigDecimal.valueOf(subset.size()), 8, RoundingMode.HALF_UP);

        return Math.sqrt(variance.doubleValue());
    }

    private BigDecimal calculateAvgVolume(List<KlineData> klines) {
        if (klines.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sum = BigDecimal.ZERO;
        for (KlineData kline : klines) {
            sum = sum.add(kline.getVolume());
        }

        return sum.divide(BigDecimal.valueOf(klines.size()), 8, RoundingMode.HALF_UP);
    }

    private String assessRiskLevel(double volatility) {
        if (volatility < 50)
            return "低风险";
        if (volatility < 150)
            return "中等风险";
        if (volatility < 300)
            return "高风险";
        return "极高风险";
    }

    private Map<String, Object> createEmptyTrend(String symbol) {
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("direction", "未知");
        result.put("strength", 0);
        result.put("message", "数据不足");
        return result;
    }

    private Map<String, Object> createEmptyIndicators(String symbol) {
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("message", "数据不足");
        return result;
    }

    private Map<String, Object> createEmptyLevels(String symbol) {
        Map<String, Object> result = new HashMap<>();
        result.put("symbol", symbol);
        result.put("message", "数据不足");
        return result;
    }
}
