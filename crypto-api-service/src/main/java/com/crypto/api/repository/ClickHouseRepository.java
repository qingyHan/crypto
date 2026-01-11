package com.crypto.api.repository;

import com.crypto.common.entity.KlineData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * ClickHouse查询Repository
 * <p>
 * 用于查询ClickHouse中的K线数据和交易数据。
 * 使用JdbcTemplate执行SQL查询，将结果映射为Java对象。
 * <p>
 * <b>主要功能：</b>
 * <ul>
 * <li><b>K线数据查询：</b>支持按交易对、时间周期、时间范围查询K线数据</li>
 * <li><b>最新K线查询：</b>获取指定交易对和周期的最新K线数据</li>
 * <li><b>24小时统计：</b>计算24小时内的最高价、最低价、成交量等统计信息</li>
 * <li><b>交易对列表：</b>查询数据库中所有交易对列表</li>
 * <li><b>数据统计：</b>统计K线数据总数和按交易对统计</li>
 * </ul>
 * <p>
 * <b>数据来源：</b>
 * 从ClickHouse数据库的{@code kline_data}表查询数据，该表由Flink作业定期写入。
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Repository
public class ClickHouseRepository {

    private final JdbcTemplate clickHouseJdbcTemplate;

    public ClickHouseRepository(@Qualifier("clickHouseJdbcTemplate") JdbcTemplate clickHouseJdbcTemplate) {
        this.clickHouseJdbcTemplate = clickHouseJdbcTemplate;
    }

    /**
     * 将 symbol 转换为数据库格式（如 BTCUSDT -> BTC-USDT）
     */
    private String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        if (symbol.contains("-")) return symbol;
        if (symbol.endsWith("USDT")) return symbol.replace("USDT", "-USDT");
        if (symbol.endsWith("BTC")) return symbol.replace("BTC", "-BTC");
        if (symbol.endsWith("ETH")) return symbol.replace("ETH", "-ETH");
        return symbol;
    }

    /**
     * 查询K线数据
     * <p>
     * 根据交易对符号、时间周期、时间范围等条件查询K线数据。
     * 支持可选的时间范围和数量限制，按开盘时间倒序排列。
     *
     * @param symbol    交易对符号，如"BTCUSDT"、"ETHUSDT"
     * @param interval  时间周期，如"1m"、"1h"、"1d"
     * @param startTime 开始时间，可选，格式：LocalDateTime
     * @param endTime   结束时间，可选，格式：LocalDateTime
     * @param limit     限制数量，可选，如果指定则限制返回数量
     * @return K线数据列表，按开盘时间倒序排列
     * @throws RuntimeException 如果查询失败
     */
    public List<KlineData> queryKlineData(String symbol, String interval,
            LocalDateTime startTime, LocalDateTime endTime, Integer limit) {
        String dbSymbol = normalizeSymbol(symbol);
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT symbol, interval, open_time, close_time, open, high, low, close, ")
                .append("volume, quote_volume, trades, insert_time ")
                .append("FROM kline_data ")
                .append("WHERE symbol = ? AND interval = ? ");

        if (startTime != null) {
            sql.append("AND open_time >= ? ");
        }
        if (endTime != null) {
            sql.append("AND open_time <= ? ");
        }

        sql.append("ORDER BY open_time DESC ");

        if (limit != null && limit > 0) {
            sql.append("LIMIT ?");
        }

        try {
            Object[] params;
            if (startTime != null && endTime != null && limit != null) {
                params = new Object[] { dbSymbol, interval, startTime, endTime, limit };
            } else if (startTime != null && endTime != null) {
                params = new Object[] { dbSymbol, interval, startTime, endTime };
            } else if (limit != null) {
                params = new Object[] { dbSymbol, interval, limit };
            } else {
                params = new Object[] { dbSymbol, interval };
            }

            List<KlineData> result = clickHouseJdbcTemplate.query(sql.toString(), new KlineDataRowMapper(), params);
            log.debug("查询K线数据：交易对={}，周期={}，返回{}条", dbSymbol, interval, result.size());
            return result;

        } catch (Exception e) {
            log.error("从ClickHouse查询K线数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("Failed to query kline data", e);
        }
    }

    /**
     * 查询最新的K线数据
     *
     * @param symbol   交易对符号
     * @param interval 时间周期
     * @return 最新的K线数据
     */
    public KlineData queryLatestKline(String symbol, String interval) {
        String dbSymbol = normalizeSymbol(symbol);
        String sql = "SELECT symbol, interval, open_time, close_time, open, high, low, close, " +
                "volume, quote_volume, trades, insert_time " +
                "FROM kline_data " +
                "WHERE symbol = ? AND interval = ? " +
                "ORDER BY open_time DESC LIMIT 1";

        try {
            List<KlineData> result = clickHouseJdbcTemplate.query(sql, new KlineDataRowMapper(),
                    dbSymbol, interval);
            return result.isEmpty() ? null : result.get(0);

        } catch (Exception e) {
            log.error("从ClickHouse查询最新K线数据失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 查询24小时统计数据
     * 使用数据库中最新24小时的数据而非当前时间计算
     *
     * @param symbol 交易对符号
     * @return 统计数据(Map格式)
     */
    public java.util.Map<String, Object> query24hStatistics(String symbol) {
        String dbSymbol = normalizeSymbol(symbol);
        // 使用1分钟K线数据计算24小时统计
        String sql = "SELECT " +
                "max(high) as high_24h, " +
                "min(low) as low_24h, " +
                "sum(volume) as volume_24h, " +
                "sum(quote_volume) as quote_volume_24h, " +
                "count(*) as kline_count " +
                "FROM kline_data " +
                "WHERE symbol = ? AND interval = '1m' " +
                "AND open_time >= (SELECT max(open_time) - INTERVAL 24 HOUR FROM kline_data WHERE symbol = ? AND interval = '1m')";

        try {
            return clickHouseJdbcTemplate.queryForMap(sql, dbSymbol, dbSymbol);
        } catch (Exception e) {
            log.error("从ClickHouse查询24小时统计数据失败：{}", e.getMessage(), e);
            return java.util.Map.of();
        }
    }

    /**
     * 查询交易对列表
     *
     * @return 交易对列表
     */
    public List<String> querySymbolList() {
        String sql = "SELECT DISTINCT symbol FROM kline_data ORDER BY symbol";

        try {
            List<String> result = clickHouseJdbcTemplate.queryForList(sql, String.class);
            return result != null ? result : List.of();
        } catch (Exception e) {
            log.error("从ClickHouse查询交易对列表失败：{}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 查询数据库中K线数据总数
     *
     * @return 总数
     */
    public Long countKlineData() {
        String sql = "SELECT COUNT(*) FROM kline_data";

        try {
            Long count = clickHouseJdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("从ClickHouse统计K线数据总数失败：{}", e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * 查询某个交易对的K线数据数量
     *
     * @param symbol 交易对符号
     * @return 数量
     */
    public Long countKlineBySymbol(String symbol) {
        String dbSymbol = normalizeSymbol(symbol);
        String sql = "SELECT COUNT(*) FROM kline_data WHERE symbol = ?";

        try {
            Long count = clickHouseJdbcTemplate.queryForObject(sql, Long.class, dbSymbol);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.error("从ClickHouse统计交易对K线数据数量失败：{}", e.getMessage(), e);
            return 0L;
        }
    }

    /**
     * K线数据RowMapper
     */
    private static class KlineDataRowMapper implements RowMapper<KlineData> {
        @Override
        public KlineData mapRow(@NonNull ResultSet rs, int rowNum) throws SQLException {
            return KlineData.builder()
                    .symbol(rs.getString("symbol"))
                    .interval(rs.getString("interval"))
                    .openTime(rs.getObject("open_time", LocalDateTime.class))
                    .closeTime(rs.getObject("close_time", LocalDateTime.class))
                    .open(rs.getBigDecimal("open"))
                    .high(rs.getBigDecimal("high"))
                    .low(rs.getBigDecimal("low"))
                    .close(rs.getBigDecimal("close"))
                    .volume(rs.getBigDecimal("volume"))
                    .quoteVolume(rs.getBigDecimal("quote_volume"))
                    .trades(rs.getInt("trades"))
                    .insertTime(rs.getObject("insert_time", LocalDateTime.class))
                    .build();
        }
    }
}
