package com.crypto.common.constants;

/**
 * Kafka主题常量定义
 * <p>
 * 集中管理系统中的所有 Kafka Topic 名称，确保生产者和消费者引用的一致性。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public final class KafkaTopics {

    private KafkaTopics() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 实时交易数据主题
     * <p>
     * <strong>Payload:</strong> {@link com.crypto.common.entity.TradeEvent} JSON<br>
     * <strong>Producer:</strong> Collector Service<br>
     * <strong>Consumer:</strong> Flink Jobs (Kline, Anomaly)<br>
     * <strong>Key:</strong> Symbol (Partition Key)
     * </p>
     */
    public static final String CRYPTO_TRADES = "crypto-trades";

    /**
     * K线数据主题
     * <p>
     * 预留用于 K 线数据的流转。目前主要直接写入 ClickHouse。
     * </p>
     */
    public static final String CRYPTO_KLINE = "crypto-kline";

    /**
     * 预警消息主题
     * <p>
     * <strong>Payload:</strong> {@link com.crypto.common.entity.AlertRecord} JSON<br>
     * <strong>Producer:</strong> Flink Alert Engine<br>
     * <strong>Consumer:</strong> API Service (Push to DB/WebSocket)
     * </p>
     */
    public static final String CRYPTO_ALERTS = "crypto-alerts";

    /**
     * 市场统计数据主题
     */
    public static final String CRYPTO_MARKET_STATS = "crypto-market-stats";
}
