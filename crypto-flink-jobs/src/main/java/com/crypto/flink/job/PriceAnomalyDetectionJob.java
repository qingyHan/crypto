package com.crypto.flink.job;

import com.crypto.common.constants.KafkaTopics;
import com.crypto.common.constants.SystemConstants;
import com.crypto.common.entity.AlertRecord;
import com.crypto.common.entity.KlineData;
import com.crypto.common.enums.AlertSeverity;
import com.crypto.common.enums.AlertType;
import com.crypto.common.utils.DateTimeUtil;
import com.crypto.common.utils.JsonUtil;
import com.crypto.flink.function.PriceChangeCalculator;
import com.crypto.flink.schema.KlineDataDeserializationSchema;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.util.ParameterTool;
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
 * 价格异常检测作业
 * <p>
 * 实时监控价格剧烈波动（暴涨/暴跌）。
 * </p>
 * 
 * <h3>检测机制：</h3>
 * 使用 <strong>滑动窗口 (Sliding Window)</strong>：
 * <ul>
 *   <li><strong>窗口大小</strong>: 5分钟 (覆盖短期趋势)</li>
 *   <li><strong>滑动步长</strong>: 1分钟 (高频检测，避免跨窗口漏报)</li>
 * </ul>
 * 
 * <p>如果 (当前价格 - 5分钟前价格) / 5分钟前价格 > 阈值，则触发预警。</p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class PriceAnomalyDetectionJob {

        public static void main(String[] args) throws Exception {
                // 解析命令行参数
                final ParameterTool params = ParameterTool.fromArgs(args);

                // 获取配置
                String kafkaServers = params.get("kafka.bootstrap.servers",
                                System.getProperty("kafka.bootstrap.servers", "kafka:29092"));

                // 创建执行环境
                StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
                env.getConfig().setGlobalJobParameters(params);

                env.setParallelism(params.getInt("parallelism", 2));
                env.enableCheckpointing(params.getLong("checkpoint.interval", 60000));

                // 创建Kafka Source读取K线数据
                KafkaSource<KlineData> source = KafkaSource.<KlineData>builder()
                                .setBootstrapServers(kafkaServers)
                                .setTopics(KafkaTopics.CRYPTO_KLINE)
                                .setGroupId("flink-price-anomaly-detection")
                                .setStartingOffsets(OffsetsInitializer.latest())
                                .setValueOnlyDeserializer(new KlineDataDeserializationSchema())
                                .build();

                // 读取K线数据流
                DataStream<KlineData> klineStream = env
                                .fromSource(source, WatermarkStrategy
                                                .<KlineData>forBoundedOutOfOrderness(Duration.ofSeconds(5))
                                                .withTimestampAssigner((kline, timestamp) -> DateTimeUtil
                                                                .localDateTimeToTimestamp(kline.getCloseTime())),
                                                "Kafka Kline Source");

                // 按交易对分组,使用5分钟滑动窗口计算价格变化
                DataStream<Tuple3<String, KlineData, KlineData>> priceChangeStream = klineStream
                                .filter(KlineData::isValid)
                                .keyBy(KlineData::getSymbol)
                                .window(SlidingEventTimeWindows.of(Duration.ofMinutes(5), Duration.ofMinutes(1))) // 窗口大小和滑动步长
                                .aggregate(new PriceChangeCalculator());
                                // 返回：(交易对, 窗口第一条K线, 窗口最后一条K线)

                // 检测异常并生成预警
                DataStream<AlertRecord> alertStream = priceChangeStream
                                .filter(tuple -> {
                                        if (tuple.f1 == null || tuple.f2 == null) {
                                                return false;
                                        }

                                        // 计算涨跌幅
                                        double changePercent = tuple.f2.getClose()
                                                        .subtract(tuple.f1.getOpen())
                                                        .divide(tuple.f1.getOpen(), 6, RoundingMode.HALF_UP)
                                                        .multiply(new BigDecimal("100"))
                                                        .doubleValue();

                                        // 判断是否超过阈值
                                        return Math.abs(changePercent) >= SystemConstants.AlertThresholds.PRICE_CHANGE_THRESHOLD;
                                })
                                .map(tuple -> {
                                        String symbol = tuple.f0;
                                        KlineData firstKline = tuple.f1;
                                        KlineData lastKline = tuple.f2;

                                        // 计算涨跌幅
                                        BigDecimal changePercent = lastKline.getClose()
                                                        .subtract(firstKline.getOpen())
                                                        .divide(firstKline.getOpen(), 4, RoundingMode.HALF_UP)
                                                        .multiply(new BigDecimal("100"));

                                        // 判断是涨还是跌
                                        AlertType alertType = changePercent.doubleValue() > 0
                                                        ? AlertType.PRICE_SPIKE
                                                        : AlertType.PRICE_DROP;

                                        // 判断严重程度
                                        AlertSeverity severity = AlertSeverity
                                                        .fromChangePercent(changePercent.doubleValue());

                                        // 构建预警消息
                                        String message = String.format("%s价格5分钟内%s%.2f%%",
                                                        symbol,
                                                        changePercent.doubleValue() > 0 ? "暴涨" : "暴跌",
                                                        Math.abs(changePercent.doubleValue()));

                                        // 元数据
                                        Map<String, Object> metadata = new HashMap<>();
                                        metadata.put("startPrice", firstKline.getOpen());
                                        metadata.put("endPrice", lastKline.getClose());
                                        metadata.put("highPrice", lastKline.getHigh());
                                        metadata.put("lowPrice", lastKline.getLow());
                                        metadata.put("timeWindow", "5m");
                                        metadata.put("startTime", DateTimeUtil.format(firstKline.getOpenTime()));
                                        metadata.put("endTime", DateTimeUtil.format(lastKline.getCloseTime()));

                                        // 创建预警记录
                                        return AlertRecord.builder()
                                                        .alertId(AlertRecord.generateAlertId())
                                                        .alertType(alertType)
                                                        .symbol(symbol)
                                                        .severity(severity)
                                                        .triggerTime(DateTimeUtil.now())
                                                        .currentPrice(lastKline.getClose())
                                                        .changePercent(changePercent)
                                                        .message(message)
                                                        .metadata(metadata)
                                                        .isRead(false)
                                                        .createdAt(DateTimeUtil.now())
                                                        .build();
                                });

                // 打印预警
                alertStream.print("ALERT");

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

                alertStream.sinkTo(kafkaSink);

                // 执行作业
                env.execute("Price Anomaly Detection Job");
        }
}
