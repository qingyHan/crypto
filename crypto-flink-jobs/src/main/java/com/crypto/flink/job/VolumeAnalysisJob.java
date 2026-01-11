package com.crypto.flink.job;

import com.crypto.common.constants.KafkaTopics;
import com.crypto.common.constants.SystemConstants;
import com.crypto.common.entity.AlertRecord;
import com.crypto.common.entity.TradeEvent;
import com.crypto.common.enums.AlertSeverity;
import com.crypto.common.enums.AlertType;
import com.crypto.common.utils.DateTimeUtil;
import com.crypto.common.utils.JsonUtil;
import com.crypto.flink.function.VolumeStatisticsAggregateFunction;
import com.crypto.flink.schema.TradeEventDeserializationSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple4;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.SlidingEventTimeWindows;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 交易量分析作业
 * <p>
 * 实时监控大额交易（巨鲸）和交易量突增。
 * </p>
 * 
 * <h3>功能模块：</h3>
 * <ol>
 *   <li><strong>巨鲸检测</strong>: 单笔交易金额超过阈值（如 100万 USD）。</li>
 *   <li><strong>异常放量</strong>: 1小时窗口内的总交易量异常。</li>
 * </ol>
 * 
 * <h3>注意：</h3>
 * <p>
 * 当前实现的异常检测逻辑为简化版（Demo Mode）：比较的是“当前窗口总金额”与“窗口内单笔平均金额”。
 * </p>
 * <p>
 * 生产环境可改进:
 * 应引入 Flink Broadcast State，将历史基准线（如昨日此时的均量）广播到所有节点，
 * 从而实现真正的“环比/同比”异常检测。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class VolumeAnalysisJob {

    public static void main(String[] args) throws Exception {
        // 解析命令行参数
        final org.apache.flink.util.ParameterTool params = org.apache.flink.util.ParameterTool.fromArgs(args);
        String kafkaServers = params.get("kafka.bootstrap.servers", "kafka:29092");

        // 创建执行环境
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(params.getInt("parallelism", 2));
        env.enableCheckpointing(60000);

        // 创建Kafka Source读取交易数据
        KafkaSource<TradeEvent> source = KafkaSource.<TradeEvent>builder()
                .setBootstrapServers(kafkaServers)
                .setTopics(KafkaTopics.CRYPTO_TRADES)
                .setGroupId("flink-volume-analysis")
                .setStartingOffsets(OffsetsInitializer.latest())
                .setValueOnlyDeserializer(new TradeEventDeserializationSchema())
                .build();

        // 读取交易数据流
        DataStream<TradeEvent> tradeStream = env
                .fromSource(source, WatermarkStrategy
                        .<TradeEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                        .withTimestampAssigner((trade, timestamp) -> trade.getEventTime()),
                        "Kafka Trade Source");

        // 1. 巨鲸交易检测（单笔交易金额超过阈值）
        DataStream<AlertRecord> whaleTradeAlerts = detectWhaleTrades(tradeStream);

        // 2. 交易量异常检测（1小时窗口内交易量异常）
        DataStream<AlertRecord> volumeAnomalyAlerts = detectVolumeAnomalies(tradeStream);

        // 合并两种预警流
        DataStream<AlertRecord> allAlerts = whaleTradeAlerts.union(volumeAnomalyAlerts);

        // 打印预警
        allAlerts.print("VOLUME_ALERT");

        // Sink到Kafka
        KafkaSink<AlertRecord> kafkaSink = KafkaSink.<AlertRecord>builder()
                .setBootstrapServers(kafkaServers)
                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                        .setTopic(KafkaTopics.CRYPTO_ALERTS)
                        .setValueSerializationSchema(new org.apache.flink.api.common.serialization.SerializationSchema<AlertRecord>() {
                            @Override
                            public byte[] serialize(AlertRecord element) {
                                return JsonUtil.toJson(element).getBytes(StandardCharsets.UTF_8);
                            }
                        })
                        .build())
                .build();

        allAlerts.sinkTo(kafkaSink);

        // 执行作业
        env.execute("Volume Analysis Job");
    }

    /**
     * 巨鲸交易检测
     * 检测单笔交易金额超过阈值的大额交易
     *
     * @param tradeStream 交易数据流
     * @return 巨鲸交易预警流
     */
    private static DataStream<AlertRecord> detectWhaleTrades(DataStream<TradeEvent> tradeStream) {
        return tradeStream
                .filter(TradeEvent::isValid)
                .filter(trade -> {
                    BigDecimal amount = trade.calculateAmount();
                    return amount.compareTo(SystemConstants.AlertThresholds.WHALE_TRADE_THRESHOLD) > 0;
                })
                .map(trade -> {
                    BigDecimal amount = trade.calculateAmount();

                    // 判断严重程度（根据交易金额）
                    AlertSeverity severity = AlertSeverity.fromWhaleTradeAmount(amount.doubleValue());

                    // 构建预警消息
                    String message = String.format("检测到%s巨鲸交易: %.2f万美元",
                            trade.getSymbol(),
                            amount.divide(new BigDecimal("10000"), 2, RoundingMode.HALF_UP).doubleValue());

                    // 元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("tradeId", trade.getTradeId());
                    metadata.put("tradeAmount", amount);
                    metadata.put("tradeAmountUsd", amount.doubleValue());
                    metadata.put("price", trade.getPrice());
                    metadata.put("quantity", trade.getQuantity());
                    metadata.put("isBuyerMaker", trade.getIsBuyerMaker());
                    metadata.put("tradeTime", DateTimeUtil.timestampToString(trade.getTradeTime()));

                    // 创建预警记录
                    return AlertRecord.builder()
                            .alertId(AlertRecord.generateAlertId())
                            .alertType(AlertType.WHALE_TRADE)
                            .symbol(trade.getSymbol())
                            .severity(severity)
                            .triggerTime(DateTimeUtil.timestampToLocalDateTime(trade.getEventTime()))
                            .currentPrice(trade.getPrice())
                            .changePercent(null)
                            .message(message)
                            .metadata(metadata)
                            .isRead(false)
                            .createdAt(DateTimeUtil.now())
                            .build();
                });
    }

    /**
     * 交易量异常检测
     * <p>
     * <strong>逻辑说明：</strong><br>
     * 计算 (当前窗口总金额 / 单笔平均金额)。<br>
     * <i>注意：此算法仅用于演示流处理的数据流转。实际业务中应计算 (当前窗口总额 / 历史平均总额)。</i>
     * </p>
     *
     * @param tradeStream 交易数据流
     * @return 交易量异常预警流
     */
    private static DataStream<AlertRecord> detectVolumeAnomalies(DataStream<TradeEvent> tradeStream) {
        // 使用1小时滑动窗口计算交易量统计
        // 返回: Tuple4<symbol, currentVolume, avgVolume, tradeCount>
        DataStream<Tuple4<String, BigDecimal, BigDecimal, Long>> volumeStats = tradeStream
                .filter(TradeEvent::isValid)
                .keyBy(TradeEvent::getSymbol)
                .window(SlidingEventTimeWindows.of(Duration.ofHours(1), Duration.ofMinutes(10)))
                .aggregate(new VolumeStatisticsAggregateFunction());

        // 检测异常并生成预警
        return volumeStats
                .filter(tuple -> {
                    BigDecimal currentVolume = tuple.f1;
                    BigDecimal avgVolume = tuple.f2;

                    // 如果平均交易量为0，跳过
                    if (avgVolume.compareTo(BigDecimal.ZERO) == 0) {
                        return false;
                    }

                    // 计算交易量倍数
                    BigDecimal volumeMultiplier = currentVolume.divide(avgVolume, 2, RoundingMode.HALF_UP);

                    // 判断是否超过阈值（3倍）
                    return volumeMultiplier.compareTo(SystemConstants.AlertThresholds.VOLUME_MULTIPLIER_THRESHOLD) > 0;
                })
                .map(tuple -> {
                    String symbol = tuple.f0;
                    BigDecimal currentVolume = tuple.f1;
                    BigDecimal avgVolume = tuple.f2;
                    Long tradeCount = tuple.f3;

                    // 计算交易量倍数
                    BigDecimal volumeMultiplier = currentVolume.divide(avgVolume, 2, RoundingMode.HALF_UP);

                    // 判断严重程度（根据倍数）
                    AlertSeverity severity = AlertSeverity.fromVolumeMultiplier(volumeMultiplier.doubleValue());

                    // 构建预警消息
                    String message = String.format("%s交易量异常: 当前1小时交易量为平均值的%.1f倍",
                            symbol,
                            volumeMultiplier.doubleValue());

                    // 元数据
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("currentVolume", currentVolume);
                    metadata.put("avgVolume", avgVolume);
                    metadata.put("volumeMultiplier", volumeMultiplier.doubleValue());
                    metadata.put("tradeCount", tradeCount);
                    metadata.put("timeWindow", "1h");

                    // 创建预警记录
                    return AlertRecord.builder()
                            .alertId(AlertRecord.generateAlertId())
                            .alertType(AlertType.VOLUME_ANOMALY)
                            .symbol(symbol)
                            .severity(severity)
                            .triggerTime(DateTimeUtil.now())
                            .currentPrice(null)
                            .changePercent(new BigDecimal(volumeMultiplier.doubleValue()))
                            .message(message)
                            .metadata(metadata)
                            .isRead(false)
                            .createdAt(DateTimeUtil.now())
                            .build();
                });
    }
}
