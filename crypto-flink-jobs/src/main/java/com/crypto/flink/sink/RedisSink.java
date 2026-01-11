package com.crypto.flink.sink;

import com.crypto.common.constants.RedisKeys;
import com.crypto.common.entity.KlineData;
import com.crypto.common.utils.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.connector.sink2.Sink;
import org.apache.flink.api.connector.sink2.SinkWriter;
import org.apache.flink.api.connector.sink2.WriterInitContext;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis Sink (Flink 2.0 API)
 * 将K线数据批量写入Redis，提升性能
 *
 * 优化点：
 * 1. 使用Pipeline批量操作
 * 2. 配置TTL常量化
 * 3. 批量flush机制
 * 4. 连接池优化
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class RedisSink implements Sink<KlineData> {

    private static final long serialVersionUID = 1L;

    // Redis TTL配置
    private static final int KLINE_TTL_SECONDS = 3600;
    private static final int PRICE_TTL_SECONDS = 60;

    // 批量操作配置
    private static final int BATCH_SIZE = 50;
    private static final long BATCH_INTERVAL_MS = 500;

    private final String redisHost;
    private final int redisPort;

    public RedisSink(String redisHost, int redisPort) {
        this.redisHost = redisHost;
        this.redisPort = redisPort;
    }

    @Override
    public SinkWriter<KlineData> createWriter(WriterInitContext context) throws IOException {
        return new RedisSinkWriter(redisHost, redisPort);
    }

    /**
     * Redis SinkWriter
     */
    private static class RedisSinkWriter implements SinkWriter<KlineData> {

        @SuppressWarnings("unused")
        private final String redisHost;
        @SuppressWarnings("unused")
        private final int redisPort;
        private JedisPool jedisPool;
        private List<KlineData> buffer;
        private long lastFlushTime;
        private long totalProcessed = 0;
        private long lastStatsUpdateTime = 0;
        private Thread heartbeatThread;

        public RedisSinkWriter(String redisHost, int redisPort) {
            this.redisHost = redisHost;
            this.redisPort = redisPort;

            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(50);
            poolConfig.setMaxIdle(20);
            poolConfig.setMinIdle(10);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(false);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setMaxWait(Duration.ofMillis(3000));

            jedisPool = new JedisPool(poolConfig, redisHost, redisPort, 5000);

            buffer = new ArrayList<>(BATCH_SIZE);
            lastFlushTime = System.currentTimeMillis();
            lastStatsUpdateTime = System.currentTimeMillis();

            log.info("Redis连接池已建立，管道模式 (批次大小={}, 间隔={}ms): {}:{}",
                    BATCH_SIZE, BATCH_INTERVAL_MS, redisHost, redisPort);

            // 初始化监控统计
            try (Jedis jedis = jedisPool.getResource()) {
                long now = System.currentTimeMillis();
                jedis.set("monitor:flink:last_update", String.valueOf(now));
                jedis.set("monitor:flink:records_processed", "0");
                jedis.set("monitor:flink:records_per_second", "0.0");
                jedis.set("monitor:flink:status", "RUNNING");
                log.info("Flink监控统计已在Redis中初始化");
            } catch (Exception e) {
                log.warn("初始化监控统计失败: {}", e.getMessage());
            }

            // 启动心跳线程，定期更新监控状态（即使没有数据）
            heartbeatThread = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(5000); // 每5秒更新一次
                        try (Jedis jedis = jedisPool.getResource()) {
                            long now = System.currentTimeMillis();
                            jedis.set("monitor:flink:last_update", String.valueOf(now));

                            // 如果长时间没有数据，更新状态为等待数据
                            long timeSinceLastData = now - lastStatsUpdateTime;
                            if (timeSinceLastData > 60000 && totalProcessed == 0) {
                                jedis.set("monitor:flink:status", "WAITING_FOR_DATA");
                                jedis.set("monitor:flink:records_per_second", "0.0");
                            } else if (totalProcessed > 0) {
                                jedis.set("monitor:flink:status", "RUNNING");
                            }
                        } catch (Exception e) {
                            log.warn("心跳更新失败: {}", e.getMessage());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("心跳线程错误: {}", e.getMessage());
                    }
                }
            }, "Flink-Monitor-Heartbeat");
            heartbeatThread.setDaemon(true);
            heartbeatThread.start();
            log.info("Flink监控心跳线程已启动");
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
                flush();
                lastFlushTime = currentTime;
            }
        }

        @Override
        public void flush(boolean endOfInput) throws IOException {
            flush();
        }

        /**
         * 使用Pipeline批量flush数据到Redis
         */
        private void flush() {
            if (buffer.isEmpty()) {
                return;
            }

            try (Jedis jedis = jedisPool.getResource()) {
                Pipeline pipeline = jedis.pipelined();
                int count = 0;

                for (KlineData klineData : buffer) {
                    String klineKey = RedisKeys.buildKlineKey(klineData.getSymbol(), klineData.getInterval());
                    String klineValue = JsonUtil.toJson(klineData);

                    if (klineValue != null) {
                        pipeline.setex(klineKey, KLINE_TTL_SECONDS, klineValue);
                        count++;
                    }

                    String priceKey = RedisKeys.buildPriceKey(klineData.getSymbol());
                    String priceValue = String.format("{\"price\":\"%s\",\"time\":%d}",
                            klineData.getClose(),
                            System.currentTimeMillis());
                    pipeline.setex(priceKey, PRICE_TTL_SECONDS, priceValue);
                }

                // 更新监控统计数据
                long currentTime = System.currentTimeMillis();
                totalProcessed += count;

                // 计算实际处理速率（基于时间间隔）
                long timeElapsed = currentTime - lastStatsUpdateTime;
                double recordsPerSecond = 0.0;
                if (timeElapsed > 0) {
                    recordsPerSecond = (count * 1000.0) / timeElapsed;
                }

                // 更新Redis监控统计
                pipeline.set("monitor:flink:last_update", String.valueOf(currentTime));
                pipeline.set("monitor:flink:records_processed", String.valueOf(totalProcessed));
                pipeline.set("monitor:flink:records_per_second", String.format("%.2f", recordsPerSecond));
                pipeline.set("monitor:flink:status", "RUNNING");

                pipeline.sync();

                lastStatsUpdateTime = currentTime;

                if (count > 0) {
                    log.debug("管道刷新 {} 条K线记录到Redis, 速率: {}/s, 总计: {}",
                            count, String.format("%.2f", recordsPerSecond), totalProcessed);
                }

                buffer.clear();

            } catch (Exception e) {
                log.error("刷新K线数据到Redis失败: {}", e.getMessage(), e);
                fallbackInsert();
            }
        }

        /**
         * 降级策略：逐条插入
         */
        private void fallbackInsert() {
            log.warn("降级为单操作模式");
            int successCount = 0;
            int failureCount = 0;
            long currentTime = System.currentTimeMillis();

            for (KlineData klineData : buffer) {
                try (Jedis jedis = jedisPool.getResource()) {
                    String klineKey = RedisKeys.buildKlineKey(klineData.getSymbol(), klineData.getInterval());
                    String klineValue = JsonUtil.toJson(klineData);

                    if (klineValue != null) {
                        jedis.setex(klineKey, KLINE_TTL_SECONDS, klineValue);
                    }

                    String priceKey = RedisKeys.buildPriceKey(klineData.getSymbol());
                    String priceValue = String.format("{\"price\":\"%s\",\"time\":%d}",
                            klineData.getClose(),
                            System.currentTimeMillis());
                    jedis.setex(priceKey, PRICE_TTL_SECONDS, priceValue);

                    successCount++;

                    // 更新监控统计
                    totalProcessed++;
                    jedis.set("monitor:flink:last_update", String.valueOf(currentTime));
                    jedis.set("monitor:flink:records_processed", String.valueOf(totalProcessed));

                    // 计算速率
                    long timeElapsed = currentTime - lastStatsUpdateTime;
                    if (timeElapsed > 1000) { // 每秒更新一次速率
                        double recordsPerSecond = (successCount * 1000.0) / timeElapsed;
                        jedis.set("monitor:flink:records_per_second", String.format("%.2f", recordsPerSecond));
                        lastStatsUpdateTime = currentTime;
                    }

                } catch (Exception e) {
                    log.error("单条K线数据存储到Redis失败: {} - {}",
                            klineData.getSymbol(), e.getMessage());
                    failureCount++;
                }
            }

            log.info("降级插入完成: 成功={}, 失败={}", successCount, failureCount);
            buffer.clear();
        }

        @Override
        public void close() throws Exception {
            // 停止心跳线程
            if (heartbeatThread != null && heartbeatThread.isAlive()) {
                heartbeatThread.interrupt();
                try {
                    heartbeatThread.join(2000);
                } catch (InterruptedException e) {
                    log.warn("等待心跳线程停止时被中断");
                }
            }

            // 最后更新一次监控状态
            try (Jedis jedis = jedisPool.getResource()) {
                jedis.set("monitor:flink:status", "STOPPED");
                jedis.set("monitor:flink:last_update", String.valueOf(System.currentTimeMillis()));
            } catch (Exception e) {
                log.warn("更新最终状态失败: {}", e.getMessage());
            }

            flush();

            if (jedisPool != null && !jedisPool.isClosed()) {
                jedisPool.close();
            }
            log.info("Redis连接池已关闭");
        }
    }
}
