package com.crypto.flink.sink;

import com.crypto.common.entity.KlineData;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;

import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * ClickHouse Sink (Flink 2.0 API)
 * 将K线数据批量写入ClickHouse，提升性能
 *
 * 优化点：
 * 1. 批量插入（BATCH_SIZE=100）
 * 2. 定时flush机制（1秒）
 * 3. PreparedStatement复用
 * 4. 事务控制
 * 5. 异常降级策略
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class ClickHouseSink implements Sink<KlineData> {

    private static final long serialVersionUID = 1L;

    // 批量插入配置
    private static final int BATCH_SIZE = 100;
    private static final long BATCH_INTERVAL_MS = 1000;

    private final String clickhouseUrl;
    private final String password;

    public ClickHouseSink(String clickhouseUrl, String password) {
        this.clickhouseUrl = clickhouseUrl;
        this.password = password;
    }

    @Override
    public SinkWriter<KlineData> createWriter(WriterInitContext context) throws IOException {
        return new ClickHouseSinkWriter(clickhouseUrl, password);
    }

    /**
     * ClickHouse SinkWriter
     */
    private static class ClickHouseSinkWriter implements SinkWriter<KlineData> {

        private final String clickhouseUrl;
        private final String password;
        private Connection connection;
        private PreparedStatement preparedStatement;
        private List<KlineData> buffer;
        private long lastFlushTime;

        public ClickHouseSinkWriter(String clickhouseUrl, String password) {
            this.clickhouseUrl = clickhouseUrl;
            this.password = password;
            this.buffer = new ArrayList<>(BATCH_SIZE);
            this.lastFlushTime = System.currentTimeMillis();

            try {
                establishConnection();
                log.info("ClickHouse连接已建立，批量模式 (批次大小={}, 间隔={}ms): {}",
                        BATCH_SIZE, BATCH_INTERVAL_MS, clickhouseUrl);
            } catch (SQLException e) {
                log.error("建立ClickHouse连接失败", e);
                throw new RuntimeException("ClickHouse连接初始化失败", e);
            }
        }

        /**
         * 建立ClickHouse连接
         */
        private void establishConnection() throws SQLException {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            Properties properties = new Properties();
            properties.setProperty("user", "default");

            // 使用构造函数传入的password
            if (password != null && !password.isEmpty()) {
                properties.setProperty("password", password);
            }

            try {
                Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("ClickHouse JDBC驱动未找到", e);
            }

            connection = DriverManager.getConnection(clickhouseUrl, properties);
            connection.setAutoCommit(false);

            String insertSql = "INSERT INTO kline_data " +
                    "(symbol, interval, open_time, close_time, open, high, low, close, volume, quote_volume, trades) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            preparedStatement = connection.prepareStatement(insertSql);

            log.info("ClickHouse连接已重新建立");
        }

        @Override
        public void write(KlineData klineData, Context context) throws IOException {
            if (klineData == null || !klineData.isValid()) {
                log.warn("无效的K线数据，已跳过");
                return;
            }

            buffer.add(klineData);

            long currentTime = System.currentTimeMillis();
            boolean sizeExceeded = buffer.size() >= BATCH_SIZE;
            boolean timeExceeded = (currentTime - lastFlushTime) >= BATCH_INTERVAL_MS;

            if (sizeExceeded || timeExceeded) {
                try {
                    flush();
                    lastFlushTime = currentTime;
                } catch (Exception e) {
                    throw new IOException("刷新数据到ClickHouse失败", e);
                }
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            try {
                flush();
            } catch (Exception e) {
                throw new IOException("刷新数据到ClickHouse失败", e);
            }
        }

        /**
         * 批量flush数据到ClickHouse
         */
        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }

            try {
                establishConnection();

                int count = 0;
                for (KlineData klineData : buffer) {
                    preparedStatement.setString(1, klineData.getSymbol());
                    preparedStatement.setString(2, klineData.getInterval());
                    preparedStatement.setTimestamp(3, Timestamp.valueOf(klineData.getOpenTime()));
                    preparedStatement.setTimestamp(4, Timestamp.valueOf(klineData.getCloseTime()));
                    preparedStatement.setBigDecimal(5, klineData.getOpen());
                    preparedStatement.setBigDecimal(6, klineData.getHigh());
                    preparedStatement.setBigDecimal(7, klineData.getLow());
                    preparedStatement.setBigDecimal(8, klineData.getClose());
                    preparedStatement.setBigDecimal(9, klineData.getVolume());
                    preparedStatement.setBigDecimal(10, klineData.getQuoteVolume());
                    preparedStatement.setInt(11, klineData.getTrades());

                    preparedStatement.addBatch();
                    count++;
                }

                int[] results = preparedStatement.executeBatch();
                connection.commit();

                log.info("批量插入 {} 条K线记录到ClickHouse (实际: {})",
                        count, results.length);

                buffer.clear();

            } catch (Exception e) {
                log.error("批量插入K线数据到ClickHouse失败: {}", e.getMessage(), e);

                try {
                    connection.rollback();
                } catch (Exception rollbackEx) {
                    log.error("事务回滚失败: {}", rollbackEx.getMessage());
                }

                fallbackInsert();
            }
        }

        /**
         * 降级策略：逐条插入
         */
        private void fallbackInsert() {
            log.warn("降级为单条插入模式");
            int successCount = 0;
            int failureCount = 0;

            for (KlineData klineData : buffer) {
                try {
                    preparedStatement.setString(1, klineData.getSymbol());
                    preparedStatement.setString(2, klineData.getInterval());
                    preparedStatement.setTimestamp(3, Timestamp.valueOf(klineData.getOpenTime()));
                    preparedStatement.setTimestamp(4, Timestamp.valueOf(klineData.getCloseTime()));
                    preparedStatement.setBigDecimal(5, klineData.getOpen());
                    preparedStatement.setBigDecimal(6, klineData.getHigh());
                    preparedStatement.setBigDecimal(7, klineData.getLow());
                    preparedStatement.setBigDecimal(8, klineData.getClose());
                    preparedStatement.setBigDecimal(9, klineData.getVolume());
                    preparedStatement.setBigDecimal(10, klineData.getQuoteVolume());
                    preparedStatement.setInt(11, klineData.getTrades());

                    preparedStatement.executeUpdate();
                    connection.commit();
                    successCount++;

                } catch (Exception e) {
                    log.error("单条K线数据插入失败: {} - {}",
                            klineData.getSymbol(), e.getMessage());
                    failureCount++;
                    try {
                        connection.rollback();
                    } catch (Exception rollbackEx) {
                        log.error("事务回滚失败: {}", rollbackEx.getMessage());
                    }
                }
            }

            log.info("降级插入完成: 成功={}, 失败={}", successCount, failureCount);
            buffer.clear();
        }

        @Override
        public void close() throws Exception {
            flush();

            if (preparedStatement != null) {
                preparedStatement.close();
            }
            if (connection != null) {
                connection.close();
            }
            log.info("ClickHouse连接已关闭");
        }
    }
}
