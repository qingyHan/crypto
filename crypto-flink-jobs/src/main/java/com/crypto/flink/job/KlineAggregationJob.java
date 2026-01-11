package com.crypto.flink.job;

import com.crypto.common.constants.KafkaTopics;
import com.crypto.common.entity.KlineData;
import com.crypto.common.entity.TradeEvent;
import com.crypto.common.utils.DateTimeUtil;
import com.crypto.common.utils.JsonUtil;
import com.crypto.flink.analytics.EnhancedAlertEngine;
import com.crypto.flink.analytics.EnhancedAlertEngine.Alert;
import com.crypto.flink.analytics.RealTimeAnalyzer;
import com.crypto.flink.analytics.RealTimeAnalyzer.AnalysisResult;
import com.crypto.flink.function.TradeToKlineAggregateFunction;
import com.crypto.flink.schema.TradeEventDeserializationSchema;
import com.crypto.flink.sink.ClickHouseSink;
import com.crypto.flink.sink.RedisSink;
import lombok.extern.slf4j.Slf4j;
import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.util.ParameterTool;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * K线聚合 Flink 作业
 * <p>
 * 系统的核心 ETL 作业。负责将无界的交易流 (Trade Stream) 聚合为有界的 K线数据 (Kline Data)。
 * </p>
 * 
 * <h3>核心逻辑：</h3>
 * <ul>
 *   <li><strong>Source</strong>: Kafka (crypto-trades) - 消费实时交易数据。</li>
 *   <li><strong>Window</strong>: 1分钟滚动事件时间窗口 (TumblingEventTimeWindows)。</li>
 *   <li><strong>Watermark</strong>: 允许 5 秒的乱序容忍度，处理网络延迟。</li>
 *   <li><strong>Aggregation</strong>: 增量聚合 (Incremental Aggregation) 计算 OHLCV。</li>
 *   <li><strong>Sink</strong>: ClickHouse (历史存储) + Redis (实时看板)。</li>
 * </ul>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
public class KlineAggregationJob {

        public static void main(String[] args) throws Exception {
                // 解析命令行参数
                final ParameterTool params = ParameterTool.fromArgs(args);

                // 获取配置，优先级：命令行参数 > 系统属性 > 默认值
                String kafkaServers = params.get("kafka.bootstrap.servers",
                                System.getProperty("kafka.bootstrap.servers", "kafka:29092"));
                String clickhouseUrl = params.get("clickhouse.url",
                                System.getProperty("clickhouse.url", "jdbc:clickhouse://clickhouse:8123/crypto_data"));
                String clickhousePassword = params.get("clickhouse.password",
                                System.getProperty("clickhouse.password", ""));
                String redisHost = params.get("redis.host",
                                System.getProperty("redis.host", "redis"));
                int redisPort = params.getInt("redis.port",
                                Integer.parseInt(System.getProperty("redis.port", "6379")));

                // 创建执行环境
                StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

                // 将参数注册为全局配置，方便在Function中获取
                env.getConfig().setGlobalJobParameters(params);

                // 设置并行度 - 默认4
                env.setParallelism(params.getInt("parallelism", 4));

                // ========== Checkpoint配置 (容错机制) ==========
                // 启用Checkpoint，间隔30秒
                env.enableCheckpointing(30000L);

                // Checkpoint配置
                var checkpointConfig = env.getCheckpointConfig();
                // 两次Checkpoint之间最小暂停时间
                checkpointConfig.setMinPauseBetweenCheckpoints(15000L);
                // Checkpoint超时时间
                checkpointConfig.setCheckpointTimeout(60000L);
                // 同时进行的Checkpoint数量
                checkpointConfig.setMaxConcurrentCheckpoints(1);

                log.info("Flink Checkpoint已启用: 间隔=30s, 超时=60s");

                // 创建Kafka Source
                KafkaSource<TradeEvent> source = KafkaSource.<TradeEvent>builder()
                            .setBootstrapServers(kafkaServers)  //kafka 服务器地址
                            .setTopics(KafkaTopics.CRYPTO_TRADES)
                            .setGroupId("flink-kline-aggregation-earliest-v2")
                            .setStartingOffsets(OffsetsInitializer.earliest())
                            .setValueOnlyDeserializer(new TradeEventDeserializationSchema())
                            .build();//构建Kafka Source

                // 读取数据流
                DataStream<TradeEvent> tradeStream = env
                            .fromSource(source, WatermarkStrategy
                                    .<TradeEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5)) //允许5秒乱序
                                    .withTimestampAssigner((event, timestamp) -> event.getEventTime())
                                    .withIdleness(Duration.ofMinutes(1)),"Kafka Source"); //数据源名称

                // 过滤无效数据
                DataStream<TradeEvent> validTradeStream = tradeStream
                            .filter(TradeEvent::isValid)
                            .name("Filter Valid Trades");

                // ============ 核心逻辑：K线聚合 ============
                // 1. keyBy(Symbol): 确保同一币种的数据进入同一个并行槽
                // 2. Window(1m): 将无限流切分为1分钟的有限块
                // 3. Aggregate: 增量计算 Open/High/Low/Close/Volume，避免缓存所有原始数据
                DataStream<KlineData> klineStream = validTradeStream
                            .keyBy(TradeEvent::getSymbol)
                            .window(TumblingEventTimeWindows.of(Duration.ofMinutes(1)))
                            .aggregate(new TradeToKlineAggregateFunction());

                // 设置interval字段
                DataStream<KlineData> enrichedKlineStream = klineStream
                            .map((MapFunction<KlineData, KlineData>) klineData -> {
                                    klineData.setInterval("1m");
                                    klineData.setInsertTime(DateTimeUtil.now());
                                    return klineData;
                            });

                // ============ 增强功能：实时数据分析与智能预警 ============
                // 这部分是系统的增强功能，对聚合后的K线数据进行深度分析

                // 应用实时分析器（趋势、异常、技术指标）
                // RealTimeAnalyzer会分析：价格趋势、波动率、RSI指标、支撑压力位等
                DataStream<AnalysisResult> analysisStream = enrichedKlineStream
                            .keyBy(KlineData::getSymbol)
                            .map(new RichMapFunction<KlineData, AnalysisResult>() {
                                    private transient Map<String, RealTimeAnalyzer> analyzers;

                                    @Override
                                    public void open(OpenContext openContext) {
                                            analyzers = new HashMap<>();
                                    }

                                    @Override
                                    public AnalysisResult map(KlineData klineData) {
                                            String symbol = klineData.getSymbol();
                                            RealTimeAnalyzer analyzer = analyzers.computeIfAbsent(
                                                            symbol, k -> new RealTimeAnalyzer(symbol));
                                            return analyzer.analyze(klineData);
                                    }
                            })
                            .name("Real-Time Analysis");

                // 打印分析报告
                analysisStream
                            .map(AnalysisResult::generateReport)
                            .print("Analysis Report");

                // 生成智能预警
                DataStream<Alert> alertStream = analysisStream
                            .map(new RichMapFunction<AnalysisResult, List<Alert>>() {
                                    private transient EnhancedAlertEngine alertEngine;

                                    @Override
                                    public void open(OpenContext openContext) {
                                            alertEngine = new EnhancedAlertEngine();
                                    }

                                    @Override
                                    public List<Alert> map(AnalysisResult analysis) {
                                            return alertEngine.analyzeAndAlert(analysis);
                                    }
                            })
                            .name("Alert Generation")
                            .flatMap((FlatMapFunction<List<Alert>, Alert>) (alerts, out) -> {
                                    for (Alert alert : alerts) {
                                            out.collect(alert);
                                    }
                            })
                            .name("Flatten Alerts");

                // 打印预警信息
                alertStream
                            .map(alert -> String.format("[%s] %s - %s: %s (评分: %d, 置信度: %.2f)",
                                            alert.getTimestamp(),
                                            alert.getSeverity().getLabel(),
                                            alert.getSymbol(),
                                            alert.getMessage(),
                                            alert.getScore(),
                                            alert.getConfidence()))
                            .print("Smart Alert");

                // ============ 数据存储（原有功能） ============

                // 创建 Kafka Sink 将 K 线数据发布到 crypto-kline topic
                // 供 PriceAnomalyDetectionJob 等下游作业消费
                KafkaSink<String> kafkaKlineSink = KafkaSink.<String>builder()
                                .setBootstrapServers(kafkaServers)
                                .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                                                .setTopic(KafkaTopics.CRYPTO_KLINE)
                                                .setValueSerializationSchema(new SimpleStringSchema())
                                                .build())
                                .build();

                // 输出 K 线数据到 Kafka
                enrichedKlineStream
                                .map(JsonUtil::toJson)
                                .sinkTo(kafkaKlineSink)
                                .name("Kafka Kline Sink");

                // Sink到ClickHouse (Flink 2.0 API)
                enrichedKlineStream.sinkTo(new ClickHouseSink(clickhouseUrl, clickhousePassword))
                                .name("ClickHouse Sink");

                // Sink到Redis (Flink 2.0 API)
                enrichedKlineStream.sinkTo(new RedisSink(redisHost, redisPort))
                                .name("Redis Sink");

                // 打印到控制台(调试用)
                enrichedKlineStream.print("K-Line");

                // 执行作业
                env.execute("Kline Aggregation Job");
        }
}