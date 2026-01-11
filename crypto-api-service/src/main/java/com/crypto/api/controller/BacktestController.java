package com.crypto.api.controller;

import com.crypto.api.dto.ApiResponse;
import com.crypto.api.dto.BacktestParams;
import com.crypto.api.dto.BacktestResult;
import com.crypto.api.service.BacktestService;
import com.crypto.common.entity.KlineData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 策略回测Controller
 * 提供交易策略的历史数据回测功能
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/backtest")
@CrossOrigin(origins = { "http://localhost:3000", "http://127.0.0.1:3000" })
public class BacktestController {

    private final BacktestService backtestService;
    private final JdbcTemplate clickHouseJdbcTemplate;

    public BacktestController(BacktestService backtestService,
            JdbcTemplate clickHouseJdbcTemplate) {
        this.backtestService = backtestService;
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
    }

    /**
     * 执行MACD策略回测
     */
    @PostMapping("/macd")
    public ApiResponse<BacktestResult> runMACDBacktest(@RequestBody BacktestParams params) {
        try {
            // 从ClickHouse查询历史K线数据
            List<KlineData> klines = loadHistoricalKlines(params);

            if (klines.isEmpty()) {
                return ApiResponse.error("No historical data found for the specified period");
            }

            // 执行回测
            BacktestResult result = backtestService.runMACDBacktest(klines, params);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("Error running MACD backtest: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 执行RSI策略回测
     */
    @PostMapping("/rsi")
    public ApiResponse<BacktestResult> runRSIBacktest(@RequestBody BacktestParams params) {
        try {
            List<KlineData> klines = loadHistoricalKlines(params);

            if (klines.isEmpty()) {
                return ApiResponse.error("No historical data found for the specified period");
            }

            BacktestResult result = backtestService.runRSIBacktest(klines, params);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("Error running RSI backtest: {}", e.getMessage(), e);
            return ApiResponse.error(e.getMessage());
        }
    }

    /**
     * 获取可用的回测策略列表
     */
    @GetMapping("/strategies")
    public ApiResponse<List<Map<String, String>>> getAvailableStrategies() {
        List<Map<String, String>> strategies = List.of(
                Map.of("id", "macd", "name", "MACD Cross Strategy",
                        "description", "基于MACD金叉死叉的交易策略"),
                Map.of("id", "rsi", "name", "RSI Oversold/Overbought Strategy",
                        "description", "基于RSI超买超卖的交易策略"),
                Map.of("id", "bollinger", "name", "Bollinger Bands Strategy",
                        "description", "基于布林带突破的交易策略(开发中)"),
                Map.of("id", "kdj", "name", "KDJ Strategy",
                        "description", "基于KDJ指标的交易策略(开发中)"));

        return ApiResponse.success(strategies);
    }

    /**
     * 将 symbol 转换为数据库格式（如 BTCUSDT -> BTC-USDT）
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null)
            return null;
        if (symbol.contains("-"))
            return symbol;
        if (symbol.endsWith("USDT"))
            return symbol.replace("USDT", "-USDT");
        if (symbol.endsWith("BTC"))
            return symbol.replace("BTC", "-BTC");
        if (symbol.endsWith("ETH"))
            return symbol.replace("ETH", "-ETH");
        return symbol;
    }

    /**
     * 从ClickHouse加载历史K线数据
     * 只映射KlineData实体类中存在的字段
     */
    @SuppressWarnings("deprecation")
    private List<KlineData> loadHistoricalKlines(BacktestParams params) {
        String dbSymbol = normalizeSymbol(params.getSymbol());

        // 将时间戳转换为字符串，避免ClickHouse JDBC的Timestamp格式问题
        String startStr = com.crypto.common.utils.DateTimeUtil.timestampToString(params.getStartTime());
        String endStr = com.crypto.common.utils.DateTimeUtil.timestampToString(params.getEndTime());

        log.info("加载历史K线数据: symbol={}, startTime={}, endTime={}", dbSymbol, startStr, endStr);

        // 注意：interval 是 ClickHouse 保留字，使用反引号转义
        String sql = "SELECT " +
                "symbol, `interval`, open_time, open, high, low, close, volume, " +
                "quote_volume, insert_time " +
                "FROM kline_data " +
                "WHERE symbol = ? " +
                "AND open_time >= ? " +
                "AND open_time <= ? " +
                "ORDER BY open_time ASC";

        return clickHouseJdbcTemplate.query(
                sql,
                new Object[] {
                        dbSymbol,
                        startStr,
                        endStr
                },
                (rs, rowNum) -> {
                    KlineData kline = new KlineData();
                    kline.setSymbol(rs.getString("symbol"));
                    kline.setInterval(rs.getString("interval"));

                    // 使用 open_time 字段
                    Timestamp openTime = rs.getTimestamp("open_time");
                    if (openTime != null) {
                        kline.setOpenTime(openTime.toLocalDateTime());
                        kline.setCloseTime(openTime.toLocalDateTime());
                    }

                    kline.setOpen(rs.getBigDecimal("open"));
                    kline.setHigh(rs.getBigDecimal("high"));
                    kline.setLow(rs.getBigDecimal("low"));
                    kline.setClose(rs.getBigDecimal("close"));
                    kline.setVolume(rs.getBigDecimal("volume"));
                    kline.setQuoteVolume(rs.getBigDecimal("quote_volume"));

                    // trades字段设置为默认值
                    kline.setTrades(0);

                    // insertTime
                    Timestamp insertTime = rs.getTimestamp("insert_time");
                    if (insertTime != null) {
                        kline.setInsertTime(insertTime.toLocalDateTime());
                    } else {
                        kline.setInsertTime(LocalDateTime.now());
                    }

                    return kline;
                });
    }
}
