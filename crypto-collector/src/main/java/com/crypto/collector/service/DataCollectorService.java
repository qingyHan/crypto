package com.crypto.collector.service;

import com.crypto.collector.client.OkxWebSocketClient;
import com.crypto.collector.config.OkxApiProperties;
import com.crypto.common.constants.KafkaTopics;
import com.crypto.common.entity.TradeEvent;
import com.crypto.common.utils.JsonUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 数据采集服务
 * <p>
 * 系统的数据入口。负责管理 WebSocket 连接生命周期，并将实时交易流转发至 Kafka。
 * 包含连接状态监控、流量统计及自动重连守护进程。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class DataCollectorService {

    private final OkxApiProperties okxApiProperties;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * WebSocket客户端映射
     * <p>
     * Key: 交易对符号（如BTC-USDT），Value: WebSocket客户端实例
     */
    private final Map<String, OkxWebSocketClient> clientMap = new ConcurrentHashMap<>();

    /**
     * 消息计数器
     * <p>
     * 统计已发送到Kafka的消息总数，使用原子操作保证线程安全
     */
    private final AtomicLong messageCount = new AtomicLong(0);

    /**
     * 上次统计更新时间
     * <p>
     * 用于计算消息速率，避免频繁更新Redis
     */
    private long lastStatsUpdateTime = System.currentTimeMillis();

    public DataCollectorService(OkxApiProperties okxApiProperties,
            KafkaTemplate<String, String> kafkaTemplate,
            RedisTemplate<String, Object> redisTemplate) {
        this.okxApiProperties = okxApiProperties;
        this.kafkaTemplate = kafkaTemplate;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 服务初始化
     * <p>
     * 1. 读取待采集的 Symbol 列表。<br>
     * 2. 建立 WebSocket 连接池。<br>
     * 3. 初始化 Redis 监控指标。<br>
     * 4. 启动连接守护线程 {@code OKX-WebSocket-Monitor}。
     * </p>
     */
    @PostConstruct
    public void init() {
        log.info("Initializing Data Collector Service with OKX...");
        List<String> symbols = okxApiProperties.getSymbols();
        log.info("Symbols to collect: {}", symbols);
        if (symbols != null) {
            //  为每个交易对创建 WebSocket 连接
            for (String symbol : symbols) {
                try {
                    connectToSymbol(symbol.trim());
                } catch (Exception e) {
                    log.error("Failed to connect to {}: {}", symbol, e.getMessage(), e);
                }
            }
        }

        log.info("Data Collector Service initialized with {} connections", clientMap.size());

        // 初始化Kafka监控统计
        try {
            long now = System.currentTimeMillis();
            redisTemplate.opsForValue().set("monitor:kafka:messages_received", 0L);
            redisTemplate.opsForValue().set("monitor:kafka:messages_per_second", 0.0);
            redisTemplate.opsForValue().set("monitor:kafka:last_message_time", now);
            redisTemplate.opsForValue().set("monitor:kafka:status", "STARTING");
            log.info("Kafka monitoring stats initialized in Redis");
        } catch (Exception e) {
            log.warn("Failed to initialize Kafka monitoring stats: {}", e.getMessage());
        }

        // 启动监控线程
        startMonitoringThread();
    }

    /**
     * 连接到指定交易对
     */
    private synchronized void connectToSymbol(String symbol) throws URISyntaxException {
        // 如果已存在且连接正常，则跳过
        OkxWebSocketClient existingClient = clientMap.get(symbol);
        if (existingClient != null && existingClient.isOpen()) {
            return;
        }

        // 如果存在但已关闭，先清理
        if (existingClient != null) {
            try {
                existingClient.close();
            } catch (Exception e) {
                /* ignore */ }
            clientMap.remove(symbol);
        }

        // OKX WebSocket URL: wss://ws.okx.com:8443/ws/v5/public
        String wsUrl = okxApiProperties.getWsUrl();
        URI uri = new URI(wsUrl);

        log.info("Connecting to OKX: {} for symbol: {}", wsUrl, symbol);

        OkxWebSocketClient client = new OkxWebSocketClient(
                uri,
                symbol,
                this::handleTradeEvent);

        try {
            client.connectBlocking(); // 使用阻塞连接以确保立即知道状态
            clientMap.put(symbol, client);
            log.info("OKX WebSocket client connected for: {}", symbol);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while connecting to {}", symbol);
        } catch (Exception e) {
            log.error("Failed to connect to {}: {}", symbol, e.getMessage());
            throw e; // 抛出异常让调用者知道失败
        }
    }

    /**
     * 处理交易事件回调
     * <p>
     * 核心转发逻辑：TradeEvent &rarr; JSON &rarr; Kafka (Key=Symbol)。
     * 异步发送以保证高吞吐，并定期更新 Redis 吞吐量统计。
     * </p>
     * 
     * @param tradeEvent 原始交易事件
     */
    private void handleTradeEvent(TradeEvent tradeEvent) {
        try {
            // 序列化为 JSON
            String json = JsonUtil.toJson(tradeEvent);
            if (json == null || json.isEmpty()) {
                log.warn("Failed to serialize trade event: {}", tradeEvent);
                return;
            }

            // 获取交易对符号作为 Kafka 的 key
            String symbol = tradeEvent.getSymbol();
            if (symbol == null || symbol.isEmpty()) {
                log.warn("Trade event has null symbol: {}", tradeEvent);
                return;
            }

            // 异步发送到 Kafka
            var future = kafkaTemplate.send(KafkaTopics.CRYPTO_TRADES, symbol, json);
            future.whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to send message to Kafka: {}", ex.getMessage());
                } else {
                    long count = messageCount.incrementAndGet();
                    long now = System.currentTimeMillis();

                    // 更新监控统计（每100条或每5秒更新一次）
                    if (count % 100 == 0 || (now - lastStatsUpdateTime) > 5000) {
                        try {
                            updateKafkaMonitoringStats(count, now);
                            lastStatsUpdateTime = now;
                        } catch (Exception e) {
                            log.warn("Failed to update Kafka monitoring stats: {}", e.getMessage());
                        }
                    }

                    if (count % 1000 == 0) {
                        log.info("Sent {} messages to Kafka", count);
                    }
                }
            });

            if (log.isDebugEnabled()) {
                log.debug("Trade Event: {} - Price: {}, Quantity: {}, Amount: {}",
                        tradeEvent.getSymbol(),
                        tradeEvent.getPrice(),
                        tradeEvent.getQuantity(),
                        tradeEvent.calculateAmount());
            }

        } catch (Exception e) {
            log.error("Error handling trade event: {}", e.getMessage(), e);
        }
    }

    /**
     * 启动连接守护线程
     * <p>
     * 每 30 秒轮询一次，检查所有客户端状态。若发现断连，则触发重连逻辑。
     * 该线程为 Daemon 线程，不阻止 JVM 关闭。
     * </p>
     */
    private void startMonitoringThread() {
        Thread monitorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30000); // 每30秒检查一次

                    log.info("=== OKX WebSocket Connection Status Check ===");
                    List<String> symbols = okxApiProperties.getSymbols();
                    if (symbols != null) {
                        for (String symbol : symbols) {
                            String key = symbol.trim();
                            OkxWebSocketClient client = clientMap.get(key);

                            boolean needsReconnect = false;
                            if (client == null) {
                                needsReconnect = true;
                                log.warn("Client for {} is missing", key);
                            } else if (client.isClosed()) {
                                needsReconnect = true;
                                log.warn("Client for {} is closed", key);
                            } else if (!client.isOpen()) {
                                // 可能是正在连接中，或者其他状态
                                log.info("Client for {} state: {}", key, client.getReadyState());
                            }

                            if (needsReconnect) {
                                log.info("Attempting to reconnect for {}", key);
                                try {
                                    connectToSymbol(key);
                                } catch (Exception e) {
                                    log.error("Reconnection failed for {}: {}", key, e.getMessage());
                                }
                            }
                        }
                    }

                    log.info("Total messages processed: {}", messageCount.get());
                    log.info("=========================================");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("Monitor thread error: {}", e.getMessage());
                }
            }
        });

        monitorThread.setName("OKX-WebSocket-Monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();

        log.info("OKX Monitoring thread started");
    }

    /**
     * 关闭所有WebSocket连接
     */
    @PreDestroy
    public void shutdown() {
        log.info("Shutting down Data Collector Service...");

        clientMap.forEach((symbol, client) -> {
            try {
                log.info("Closing WebSocket connection for: {}", symbol);
                client.close();
            } catch (Exception e) {
                log.error("Error closing WebSocket for {}: {}", symbol, e.getMessage());
            }
        });

        clientMap.clear();
        log.info("Data Collector Service shutdown complete. Total messages: {}", messageCount.get());
    }

    /**
     * 更新Kafka监控统计到Redis
     */
    private void updateKafkaMonitoringStats(long totalCount, long currentTime) {
        try {
            // 计算消息速率（每秒）
            long timeElapsed = currentTime - lastStatsUpdateTime;
            double messagesPerSecond = 0.0;
            if (timeElapsed > 0) {
                messagesPerSecond = 100.0 * 1000.0 / timeElapsed; // 每100条消息的速率
            }

            // 更新Redis监控统计
            redisTemplate.opsForValue().set("monitor:kafka:messages_received", totalCount);
            redisTemplate.opsForValue().set("monitor:kafka:messages_per_second", messagesPerSecond);
            redisTemplate.opsForValue().set("monitor:kafka:last_message_time", currentTime);
            redisTemplate.opsForValue().set("monitor:kafka:status", "RUNNING");

            log.debug("Updated Kafka monitoring stats: total={}, rate={}/s",
                    totalCount, String.format("%.2f", messagesPerSecond));
        } catch (Exception e) {
            log.warn("Failed to update Kafka monitoring stats: {}", e.getMessage());
        }
    }

    /**
     * 获取连接状态
     */
    public Map<String, Object> getConnectionStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("totalConnections", clientMap.size());
        status.put("messagesProcessed", messageCount.get());

        Map<String, String> connectionDetails = new ConcurrentHashMap<>();
        clientMap.forEach((symbol, client) -> {
            connectionDetails.put(symbol, client.getReadyState().toString());
        });
        status.put("connections", connectionDetails);

        return status;
    }
}
