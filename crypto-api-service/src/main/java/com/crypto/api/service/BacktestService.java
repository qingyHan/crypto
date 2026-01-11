package com.crypto.api.service;

import com.crypto.api.dto.MACDResult;
import com.crypto.api.dto.BacktestResult;
import com.crypto.api.dto.Trade;
import com.crypto.api.dto.EquityPoint;
import com.crypto.api.dto.BacktestParams;
import com.crypto.common.entity.KlineData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.util.*;

/**
 * 策略回测服务
 * 支持基于历史数据进行交易策略回测
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class BacktestService {

    /**
     * 计算简化版MACD指标
     * 使用EMA12, EMA26, Signal=EMA9(MACD)
     */
    private MACDResult calculateSimplifiedMACD(List<KlineData> klines) {
        if (klines.size() < 26) {
            MACDResult result = new MACDResult();
            result.setSignalType("HOLD");
            return result;
        }

        // 计算EMA12和EMA26
        double ema12 = calculateEMA(klines, 12);
        double ema26 = calculateEMA(klines, 26);
        double macd = ema12 - ema26;

        // 计算Signal线(MACD的9日EMA)
        // 简化实现：使用最近9个MACD值的EMA
        double signal = macd; // 简化版，直接使用当前MACD作为signal
        if (klines.size() >= 34) {
            // 如果数据足够，计算更准确的signal
            List<Double> macdValues = new ArrayList<>();
            for (int i = Math.max(0, klines.size() - 34); i < klines.size(); i++) {
                List<KlineData> window = klines.subList(0, i + 1);
                if (window.size() >= 26) {
                    double e12 = calculateEMA(window, 12);
                    double e26 = calculateEMA(window, 26);
                    macdValues.add(e12 - e26);
                }
            }
            if (!macdValues.isEmpty()) {
                signal = calculateSimpleEMA(macdValues, 9);
            }
        }

        double histogram = macd - signal;

        MACDResult result = new MACDResult();
        result.setMacd(macd);
        result.setSignal(signal);
        result.setHistogram(histogram);

        // 生成交易信号
        if (histogram > 0 && macd > signal) {
            result.setSignalType("BUY");
        } else if (histogram < 0 && macd < signal) {
            result.setSignalType("SELL");
        } else {
            result.setSignalType("HOLD");
        }

        return result;
    }

    /**
     * 计算EMA (指数移动平均)
     */
    private double calculateEMA(List<KlineData> klines, int period) {
        if (klines.size() < period) {
            return klines.stream()
                    .mapToDouble(k -> k.getClose().doubleValue())
                    .average()
                    .orElse(0.0);
        }

        double multiplier = 2.0 / (period + 1);

        // 初始EMA使用SMA
        double ema = klines.subList(0, period).stream()
                .mapToDouble(k -> k.getClose().doubleValue())
                .average()
                .orElse(0.0);

        // 计算EMA
        for (int i = period; i < klines.size(); i++) {
            double price = klines.get(i).getClose().doubleValue();
            ema = (price - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * 计算简单EMA (用于double列表)
     */
    private double calculateSimpleEMA(List<Double> values, int period) {
        if (values.isEmpty()) {
            return 0.0;
        }

        int actualPeriod = Math.min(period, values.size());
        double multiplier = 2.0 / (actualPeriod + 1);

        double ema = values.subList(0, actualPeriod).stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(0.0);

        for (int i = actualPeriod; i < values.size(); i++) {
            ema = (values.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * 计算简化版RSI指标
     */
    private double calculateSimplifiedRSI(List<KlineData> klines, int period) {
        if (klines.size() < period + 1) {
            return 50.0; // 默认中性值
        }

        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();

        // 计算价格变动
        for (int i = klines.size() - period; i < klines.size(); i++) {
            if (i > 0) {
                BigDecimal change = klines.get(i).getClose().subtract(klines.get(i - 1).getClose());
                if (change.compareTo(BigDecimal.ZERO) > 0) {
                    gains.add(change);
                    losses.add(BigDecimal.ZERO);
                } else {
                    gains.add(BigDecimal.ZERO);
                    losses.add(change.abs());
                }
            }
        }

        // 计算平均涨跌幅
        BigDecimal avgGain = gains.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        BigDecimal avgLoss = losses.stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(period), 8, RoundingMode.HALF_UP);

        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return 100.0;
        }

        // RSI = 100 - (100 / (1 + RS))
        BigDecimal rs = avgGain.divide(avgLoss, 8, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(
                        BigDecimal.ONE.add(rs), 8, RoundingMode.HALF_UP));

        return rsi.doubleValue();
    }

    /**
     * 执行MACD策略回测
     */
    public BacktestResult runMACDBacktest(List<KlineData> klines, BacktestParams params) {
        if (klines == null || klines.size() < 34) {
            throw new IllegalArgumentException("Insufficient data for MACD backtest");
        }

        BacktestResult result = new BacktestResult();
        result.setStrategyName("MACD Cross Strategy");
        result.setTrades(new ArrayList<>());
        result.setEquityCurve(new ArrayList<>());

        BigDecimal cash = params.getInitialCash();
        BigDecimal position = BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.ZERO;

        String previousSignal = "HOLD";
        List<BigDecimal> returns = new ArrayList<>();

        for (int i = 34; i < klines.size(); i++) {
            List<KlineData> window = klines.subList(0, i + 1);
            MACDResult macd = calculateSimplifiedMACD(window);

            KlineData currentKline = klines.get(i);
            BigDecimal currentPrice = currentKline.getClose();

            // 生成交易信号
            String signal = macd.getSignalType();

            // 记录资金曲线
            BigDecimal positionValue = position.multiply(currentPrice);
            BigDecimal totalEquity = cash.add(positionValue);

            EquityPoint equityPoint = new EquityPoint();
            equityPoint
                    .setTimestamp(currentKline.getOpenTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            equityPoint.setEquity(totalEquity);
            equityPoint.setCash(cash);
            equityPoint.setPosition(positionValue);
            result.getEquityCurve().add(equityPoint);

            // 执行交易逻辑
            if (signal.equals("BUY") && !previousSignal.equals("BUY") && cash.compareTo(BigDecimal.ZERO) > 0) {
                // 买入信号
                BigDecimal buyAmount = cash.multiply(BigDecimal.valueOf(0.95)); // 使用95%资金
                quantity = buyAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);
                position = position.add(quantity);
                cash = cash.subtract(buyAmount);

                Trade trade = new Trade();
                trade.setTimestamp(
                        currentKline.getOpenTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                trade.setType("BUY");
                trade.setPrice(currentPrice);
                trade.setQuantity(quantity);
                trade.setSignal("MACD BUY");
                result.getTrades().add(trade);

                log.debug("BUY: price={}, quantity={}, cash={}", currentPrice, quantity, cash);

            } else if (signal.equals("SELL") && !previousSignal.equals("SELL")
                    && position.compareTo(BigDecimal.ZERO) > 0) {
                // 卖出信号
                BigDecimal sellValue = position.multiply(currentPrice);

                // 计算盈亏
                BigDecimal profit = sellValue.subtract(
                        result.getTrades().stream()
                                .filter(t -> t.getType().equals("BUY"))
                                .map(t -> t.getPrice().multiply(t.getQuantity()))
                                .reduce(BigDecimal.ZERO, BigDecimal::add));

                cash = cash.add(sellValue);
                position = BigDecimal.ZERO;

                Trade trade = new Trade();
                trade.setTimestamp(
                        currentKline.getOpenTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                trade.setType("SELL");
                trade.setPrice(currentPrice);
                trade.setQuantity(quantity);
                trade.setProfit(profit);
                trade.setSignal("MACD SELL");
                result.getTrades().add(trade);

                // 计算收益率
                BigDecimal returnRate = profit.divide(params.getInitialCash(), 8, RoundingMode.HALF_UP);
                returns.add(returnRate);

                log.debug("SELL: price={}, quantity={}, profit={}, cash={}",
                        currentPrice, quantity, profit, cash);

                quantity = BigDecimal.ZERO;
            }

            previousSignal = signal;
        }

        // 计算最终资产(如果还有持仓,按最后价格卖出)
        if (position.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lastPrice = klines.get(klines.size() - 1).getClose();
            cash = cash.add(position.multiply(lastPrice));
            position = BigDecimal.ZERO;
        }

        // 计算回测指标
        calculateBacktestMetrics(result, params.getInitialCash(), cash, returns);

        return result;
    }

    /**
     * 执行RSI策略回测
     */
    public BacktestResult runRSIBacktest(List<KlineData> klines, BacktestParams params) {
        if (klines == null || klines.size() < 15) {
            throw new IllegalArgumentException("Insufficient data for RSI backtest");
        }

        BacktestResult result = new BacktestResult();
        result.setStrategyName("RSI Oversold/Overbought Strategy");
        result.setTrades(new ArrayList<>());
        result.setEquityCurve(new ArrayList<>());

        // RSI阈值
        double oversoldThreshold = 30.0;
        double overboughtThreshold = 70.0;

        BigDecimal cash = params.getInitialCash();
        BigDecimal position = BigDecimal.ZERO;
        BigDecimal quantity = BigDecimal.ZERO;

        List<BigDecimal> returns = new ArrayList<>();

        for (int i = 15; i < klines.size(); i++) {
            List<KlineData> window = klines.subList(0, i + 1);
            double rsi = calculateSimplifiedRSI(window, 14);

            KlineData currentKline = klines.get(i);
            BigDecimal currentPrice = currentKline.getClose();

            // 记录资金曲线
            BigDecimal positionValue = position.multiply(currentPrice);
            BigDecimal totalEquity = cash.add(positionValue);

            EquityPoint equityPoint = new EquityPoint();
            equityPoint
                    .setTimestamp(currentKline.getOpenTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            equityPoint.setEquity(totalEquity);
            equityPoint.setCash(cash);
            equityPoint.setPosition(positionValue);
            result.getEquityCurve().add(equityPoint);

            // RSI交易逻辑
            if (rsi < oversoldThreshold && position.compareTo(BigDecimal.ZERO) == 0) {
                // 超卖,买入
                BigDecimal buyAmount = cash.multiply(BigDecimal.valueOf(0.95));
                quantity = buyAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);
                position = position.add(quantity);
                cash = cash.subtract(buyAmount);

                Trade trade = new Trade();
                trade.setTimestamp(
                        currentKline.getOpenTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                trade.setType("BUY");
                trade.setPrice(currentPrice);
                trade.setQuantity(quantity);
                trade.setSignal("RSI OVERSOLD (" + String.format("%.2f", rsi) + ")");
                result.getTrades().add(trade);

            } else if (rsi > overboughtThreshold && position.compareTo(BigDecimal.ZERO) > 0) {
                // 超买,卖出
                BigDecimal sellValue = position.multiply(currentPrice);
                BigDecimal buyValue = result.getTrades().stream()
                        .filter(t -> t.getType().equals("BUY"))
                        .map(t -> t.getPrice().multiply(t.getQuantity()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal profit = sellValue.subtract(buyValue);
                cash = cash.add(sellValue);
                position = BigDecimal.ZERO;

                Trade trade = new Trade();
                trade.setTimestamp(
                        currentKline.getOpenTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                trade.setType("SELL");
                trade.setPrice(currentPrice);
                trade.setQuantity(quantity);
                trade.setProfit(profit);
                trade.setSignal("RSI OVERBOUGHT (" + String.format("%.2f", rsi) + ")");
                result.getTrades().add(trade);

                BigDecimal returnRate = profit.divide(params.getInitialCash(), 8, RoundingMode.HALF_UP);
                returns.add(returnRate);

                quantity = BigDecimal.ZERO;
            }
        }

        // 平仓
        if (position.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lastPrice = klines.get(klines.size() - 1).getClose();
            cash = cash.add(position.multiply(lastPrice));
            position = BigDecimal.ZERO;
        }

        calculateBacktestMetrics(result, params.getInitialCash(), cash, returns);

        return result;
    }

    /**
     * 计算回测指标
     */
    private void calculateBacktestMetrics(BacktestResult result, BigDecimal initialCash,
            BigDecimal finalCash, List<BigDecimal> returns) {
        // 总收益率
        BigDecimal totalReturn = finalCash.subtract(initialCash)
                .divide(initialCash, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        result.setTotalReturn(totalReturn.doubleValue());

        // 统计交易
        long sellCount = result.getTrades().stream().filter(t -> t.getType().equals("SELL")).count();
        result.setTotalTrades((int) sellCount);

        // 盈亏统计
        long winCount = result.getTrades().stream()
                .filter(t -> t.getType().equals("SELL") && t.getProfit() != null
                        && t.getProfit().compareTo(BigDecimal.ZERO) > 0)
                .count();
        long lossCount = result.getTrades().stream()
                .filter(t -> t.getType().equals("SELL") && t.getProfit() != null
                        && t.getProfit().compareTo(BigDecimal.ZERO) < 0)
                .count();

        result.setWinningTrades((int) winCount);
        result.setLosingTrades((int) lossCount);
        result.setWinRate(sellCount > 0 ? (double) winCount / sellCount * 100 : 0.0);

        // 平均盈亏
        if (!returns.isEmpty()) {
            double avgProfit = result.getTrades().stream()
                    .filter(t -> t.getType().equals("SELL") && t.getProfit() != null
                            && t.getProfit().compareTo(BigDecimal.ZERO) > 0)
                    .mapToDouble(t -> t.getProfit().divide(initialCash, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).doubleValue())
                    .average()
                    .orElse(0.0);

            double avgLoss = result.getTrades().stream()
                    .filter(t -> t.getType().equals("SELL") && t.getProfit() != null
                            && t.getProfit().compareTo(BigDecimal.ZERO) < 0)
                    .mapToDouble(t -> t.getProfit().divide(initialCash, 8, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).doubleValue())
                    .average()
                    .orElse(0.0);

            result.setAvgProfit(avgProfit);
            result.setAvgLoss(Math.abs(avgLoss));
        }

        // 最大回撤
        BigDecimal maxEquity = initialCash;
        BigDecimal maxDrawdown = BigDecimal.ZERO;

        for (EquityPoint point : result.getEquityCurve()) {
            if (point.getEquity().compareTo(maxEquity) > 0) {
                maxEquity = point.getEquity();
            }

            BigDecimal drawdown = maxEquity.subtract(point.getEquity())
                    .divide(maxEquity, 8, RoundingMode.HALF_UP);

            if (drawdown.compareTo(maxDrawdown) > 0) {
                maxDrawdown = drawdown;
            }
        }
        result.setMaxDrawdown(maxDrawdown.multiply(BigDecimal.valueOf(100)).doubleValue());

        // 夏普比率(简化计算)
        if (!returns.isEmpty()) {
            double avgReturn = returns.stream()
                    .mapToDouble(BigDecimal::doubleValue)
                    .average()
                    .orElse(0.0);

            double stdDev = Math.sqrt(returns.stream()
                    .mapToDouble(r -> Math.pow(r.doubleValue() - avgReturn, 2))
                    .average()
                    .orElse(0.0));

            result.setSharpeRatio(stdDev > 0 ? avgReturn / stdDev : 0.0);
        } else {
            result.setSharpeRatio(0.0);
        }

        // 年化收益率(假设数据为一年)
        result.setAnnualizedReturn(result.getTotalReturn());
    }
}
