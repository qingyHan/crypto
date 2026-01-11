package com.crypto.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 数据流监控控制器
 * <p>
 * 提供Kafka、Flink、ClickHouse、Redis等组件的实时监控数据，是系统运维监控的核心接口。
 * <p>
 * 主要功能：
 * <ul>
 * <li>Kafka监控：消息消费统计、消费延迟、吞吐量等</li>
 * <li>Flink监控：作业状态、任务数量、处理速度等</li>
 * <li>ClickHouse监控：写入统计、表大小、写入速度等</li>
 * <li>Redis监控：缓存命中率、内存使用、键数量等</li>
 * <li>数据流概览：整体系统健康状态</li>
 * <li>实时吞吐量：最近60秒的吞吐量数据</li>
 * </ul>
 * <p>
 * 数据来源：
 * <ul>
 * <li>Kafka/Flink/ClickHouse/Redis统计：从Redis缓存获取（由各组件定期更新）</li>
 * <li>Flink作业状态：直接从Flink JobManager REST API获取（实时状态）</li>
 * </ul>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/monitor")
@CrossOrigin(origins = { "http://localhost:3000", "http://127.0.0.1:3000" })
public class DataFlowMonitorController {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate flinkRestTemplate;

    public DataFlowMonitorController(RedisTemplate<String, Object> redisTemplate,
            @org.springframework.beans.factory.annotation.Qualifier("flinkRestTemplate") RestTemplate flinkRestTemplate) {
        this.redisTemplate = redisTemplate;
        this.flinkRestTemplate = flinkRestTemplate;
    }

    /**
     * 获取Kafka消费统计
     * <p>
     * 获取Kafka消息消费的统计信息，包括消息接收数量、每秒消息数、消费延迟等。
     * <p>
     * 数据来源：从Redis缓存获取，由Kafka消费者定期更新。
     * <p>
     * 示例请求：{@code GET /api/monitor/kafka/stats}
     *
     * @return Kafka统计信息Map，包含消息接收数、每秒消息数、消费延迟、状态等
     */
    @GetMapping("/kafka/stats")
    public Map<String, Object> getKafkaStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 从Redis获取Kafka统计信息
            Object messagesReceived = redisTemplate.opsForValue().get("monitor:kafka:messages_received");
            Object messagesPerSecond = redisTemplate.opsForValue().get("monitor:kafka:messages_per_second");
            Object lastMessageTime = redisTemplate.opsForValue().get("monitor:kafka:last_message_time");
            Object consumerLag = redisTemplate.opsForValue().get("monitor:kafka:consumer_lag");

            // 转换数据类型（处理Redis返回的Object类型）
            long received = messagesReceived != null
                    ? (messagesReceived instanceof Number ? ((Number) messagesReceived).longValue()
                            : Long.parseLong(messagesReceived.toString()))
                    : 0L;
            double mps = messagesPerSecond != null
                    ? (messagesPerSecond instanceof Number ? ((Number) messagesPerSecond).doubleValue()
                            : Double.parseDouble(messagesPerSecond.toString()))
                    : 0.0;
            long lag = consumerLag != null
                    ? (consumerLag instanceof Number ? ((Number) consumerLag).longValue()
                            : Long.parseLong(consumerLag.toString()))
                    : 0L;

            stats.put("messagesReceived", received);
            stats.put("messagesPerSecond", mps);
            stats.put("lastMessageTime", lastMessageTime != null ? lastMessageTime : System.currentTimeMillis());
            stats.put("consumerLag", lag);
            stats.put("lag", lag); // 别名
            stats.put("status", received > 0 ? "RUNNING" : "STARTING");
            stats.put("topics", Arrays.asList("crypto-trades", "crypto-alerts"));

        } catch (Exception e) {
            log.error("获取Kafka统计信息失败：{}", e.getMessage(), e);
            stats.put("status", "ERROR");
            stats.put("messagesReceived", 0L);
            stats.put("messagesPerSecond", 0.0);
            stats.put("consumerLag", 0L);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 获取Flink作业统计 - 直接从Flink REST API获取真实状态
     * 
     * 本接口用于监控Flink流处理作业的运行状态，通过调用Flink JobManager的REST API
     * 获取实时的作业信息，而不是依赖可能过时的Redis缓存数据。
     * 
     * 获取的信息包括：
     * - 作业状态（RUNNING、FAILED、CANCELED等）
     * - 作业名称和运行时长
     * - 运行中的任务数量
     * - 并行度配置
     * 
     * 数据来源：
     * - Flink JobManager REST API：http://flink-jobmanager:8081/jobs/overview
     * - 如果无法连接Flink API，返回DISCONNECTED状态
     * 
     * 使用场景：
     * - 前端仪表盘显示Flink作业状态
     * - 系统监控和运维管理
     * - 故障排查和性能分析
     * 
     * @return Flink作业统计信息，包含状态、任务数、运行时长等
     */
    @GetMapping("/flink/jobs")
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Map<String, Object> getFlinkJobStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 使用注入的RestTemplate（避免每次创建新实例）
            String flinkUrl = "http://flink-jobmanager:8081/jobs/overview";

            String status = "STARTING";
            long processed = 0L;
            double rps = 0.0;
            int runningTasks = 0;
            String jobName = "Kline Aggregation Job";
            long duration = 0L;

            try {
                ResponseEntity<Map> response = flinkRestTemplate.getForEntity(flinkUrl, Map.class);
                Map<String, Object> body = response.getBody();
                if (response.getStatusCode().is2xxSuccessful() && body != null) {
                    List<Map<String, Object>> jobs = (List<Map<String, Object>>) body.get("jobs");

                    if (jobs != null && !jobs.isEmpty()) {
                        // 遍历查找正在运行的作业
                        Map<String, Object> targetJob = jobs.get(0); // 默认取第一个

                        log.info("找到{}个Flink作业", jobs.size());
                        for (Map<String, Object> job : jobs) {
                            String state = (String) job.get("state");
                            log.info("检查作业：名称={}，状态={}", job.get("name"), state);
                            if ("RUNNING".equalsIgnoreCase(state)) {
                                targetJob = job;
                                break;
                            }
                        }

                        String jobState = (String) targetJob.get("state");
                        jobName = (String) targetJob.getOrDefault("name", "Kline Aggregation Job");
                        duration = ((Number) targetJob.getOrDefault("duration", 0)).longValue();

                        Map<String, Object> tasks = (Map<String, Object>) targetJob.get("tasks");
                        if (tasks != null) {
                            runningTasks = ((Number) tasks.getOrDefault("running", 0)).intValue();
                        }

                        // 直接使用Flink返回的状态
                        if ("RUNNING".equalsIgnoreCase(jobState)) {
                            status = "RUNNING";
                        } else if ("FAILED".equalsIgnoreCase(jobState)) {
                            status = "FAILED";
                        } else if ("CANCELED".equalsIgnoreCase(jobState)) {
                            status = "STOPPED";
                        } else {
                            status = jobState != null ? jobState : "STARTING";
                        }
                    }
                }
            } catch (Exception flinkEx) {
                log.warn("无法连接Flink REST API: {}", flinkEx.getMessage());
                status = "DISCONNECTED";
            }

            stats.put("recordsProcessed", processed);
            stats.put("recordsPerSecond", rps);
            stats.put("checkpointCount", 0L);
            stats.put("lastCheckpointTime", System.currentTimeMillis());
            stats.put("parallelism", 4);
            stats.put("status", status);
            stats.put("runningTasks", runningTasks);

            // 作业列表
            List<Map<String, Object>> jobList = new ArrayList<>();
            Map<String, Object> job1 = new HashMap<>();
            job1.put("name", jobName);
            job1.put("status", status);
            job1.put("uptime", formatDuration(duration));
            job1.put("recordsProcessed", processed);
            job1.put("recordsPerSecond", rps);
            job1.put("throughput", rps);
            job1.put("totalProcessed", processed);
            job1.put("runningTasks", runningTasks);
            jobList.add(job1);

            stats.put("jobs", jobList);

        } catch (Exception e) {
            log.error("获取Flink作业统计信息失败：{}", e.getMessage(), e);
            stats.put("status", "ERROR");
            stats.put("recordsProcessed", 0L);
            stats.put("recordsPerSecond", 0.0);
            stats.put("jobs", new ArrayList<>());
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    private String formatDuration(long millis) {
        if (millis <= 0)
            return "0s";
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        return seconds + "s";
    }

    /**
     * 检查Flink是否可用
     * <p>
     * 通过检查Redis中Flink的最后更新时间来判断Flink是否正在运行。
     * <p>
     * 判断逻辑：如果最近2分钟内有更新，认为Flink正在运行。
     *
     * @return true表示Flink可用，false表示不可用
     */
    @SuppressWarnings("unused")
    private boolean checkFlinkAvailability() {
        try {
            // 检查最近是否有数据写入Redis
            Object lastUpdateTime = redisTemplate.opsForValue().get("monitor:flink:last_update");
            if (lastUpdateTime != null) {
                long lastUpdate = ((Number) lastUpdateTime).longValue();
                // 如果最近2分钟内有更新，认为Flink正在运行
                return (System.currentTimeMillis() - lastUpdate) < 120000;
            }
            return false;
        } catch (Exception e) {
            log.warn("检查Flink可用性失败：{}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取ClickHouse写入统计
     * <p>
     * 获取ClickHouse数据库的写入统计信息，包括写入记录数、每秒写入数、表大小等。
     * <p>
     * 数据来源：从Redis缓存获取，由Flink作业定期更新。
     * <p>
     * 示例请求：{@code GET /api/monitor/clickhouse/stats}
     *
     * @return ClickHouse统计信息Map，包含写入记录数、写入速度、表大小、状态等
     */
    @GetMapping("/clickhouse/stats")
    public Map<String, Object> getClickHouseStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 从Redis获取ClickHouse统计
            Object recordsWritten = redisTemplate.opsForValue().get("monitor:clickhouse:records_written");
            Object writesPerSecond = redisTemplate.opsForValue().get("monitor:clickhouse:writes_per_second");
            Object lastWriteTime = redisTemplate.opsForValue().get("monitor:clickhouse:last_write_time");
            Object tableSize = redisTemplate.opsForValue().get("monitor:clickhouse:table_size");

            stats.put("recordsWritten", recordsWritten != null ? recordsWritten : 0L);
            stats.put("writesPerSecond", writesPerSecond != null ? writesPerSecond : 0.0);
            stats.put("lastWriteTime", lastWriteTime != null ? lastWriteTime : System.currentTimeMillis());
            stats.put("tableSize", tableSize != null ? tableSize : "0 MB");
            stats.put("status", "RUNNING");
            stats.put("tables", Arrays.asList("kline_data", "alert_records", "trade_details"));

        } catch (Exception e) {
            log.error("获取ClickHouse统计信息失败：{}", e.getMessage(), e);
            stats.put("status", "ERROR");
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 获取Redis缓存统计
     * <p>
     * 获取Redis缓存的统计信息，包括缓存命中率、内存使用、键数量等。
     * <p>
     * 数据来源：从Redis缓存获取，由系统定期更新。
     * <p>
     * 示例请求：{@code GET /api/monitor/redis/stats}
     *
     * @return Redis统计信息Map，包含缓存命中数、未命中数、键数量、内存使用、命中率等
     */
    @GetMapping("/redis/stats")
    public Map<String, Object> getRedisStats() {
        Map<String, Object> stats = new HashMap<>();

        try {
            // 从Redis获取缓存统计
            Object cacheHits = redisTemplate.opsForValue().get("monitor:redis:cache_hits");
            Object cacheMisses = redisTemplate.opsForValue().get("monitor:redis:cache_misses");
            Object keysCount = redisTemplate.opsForValue().get("monitor:redis:keys_count");
            Object memoryUsed = redisTemplate.opsForValue().get("monitor:redis:memory_used");

            stats.put("cacheHits", cacheHits != null ? cacheHits : 0L);
            stats.put("cacheMisses", cacheMisses != null ? cacheMisses : 0L);
            stats.put("keysCount", keysCount != null ? keysCount : 0L);
            stats.put("memoryUsed", memoryUsed != null ? memoryUsed : "0 MB");
            stats.put("status", "RUNNING");

            // 计算缓存命中率（命中数 / (命中数 + 未命中数) * 100%）
            long hits = cacheHits != null ? ((Number) cacheHits).longValue() : 0L;
            long misses = cacheMisses != null ? ((Number) cacheMisses).longValue() : 0L;
            double hitRate = (hits + misses) > 0 ? (double) hits / (hits + misses) * 100 : 0.0;
            stats.put("hitRate", String.format("%.2f%%", hitRate));

        } catch (Exception e) {
            log.error("获取Redis统计信息失败：{}", e.getMessage(), e);
            stats.put("status", "ERROR");
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    /**
     * 获取完整的数据流监控概览
     * <p>
     * 获取所有组件的监控数据，并判断整体系统健康状态。
     * <p>
     * 健康状态判断：
     * <ul>
     * <li>HEALTHY：所有组件状态为RUNNING</li>
     * <li>ERROR：任一组件状态为ERROR</li>
     * <li>STARTING：其他情况</li>
     * </ul>
     * <p>
     * 示例请求：{@code GET /api/monitor/overview}
     *
     * @return 数据流概览Map，包含Kafka、Flink、ClickHouse、Redis的统计信息和整体状态
     */
    @GetMapping("/overview")
    public Map<String, Object> getDataFlowOverview() {
        Map<String, Object> overview = new HashMap<>();

        try {
            Map<String, Object> kafkaStats = getKafkaStats();
            Map<String, Object> flinkStats = getFlinkJobStats();
            Map<String, Object> clickhouseStats = getClickHouseStats();
            Map<String, Object> redisStats = getRedisStats();

            overview.put("kafka", kafkaStats);
            overview.put("flink", flinkStats);
            overview.put("clickhouse", clickhouseStats);
            overview.put("redis", redisStats);
            overview.put("timestamp", System.currentTimeMillis());

            // 判断整体系统健康状态
            String kafkaStatus = (String) kafkaStats.getOrDefault("status", "STARTING");
            String flinkStatus = (String) flinkStats.getOrDefault("status", "STARTING");
            String clickhouseStatus = (String) clickhouseStats.getOrDefault("status", "STARTING");

            if ("RUNNING".equals(kafkaStatus) && "RUNNING".equals(flinkStatus) && "RUNNING".equals(clickhouseStatus)) {
                overview.put("status", "HEALTHY");
            } else if ("ERROR".equals(kafkaStatus) || "ERROR".equals(flinkStatus) || "ERROR".equals(clickhouseStatus)) {
                overview.put("status", "ERROR");
            } else {
                overview.put("status", "STARTING");
            }

        } catch (Exception e) {
            log.error("获取数据流概览失败：{}", e.getMessage(), e);
            overview.put("status", "ERROR");
            overview.put("error", e.getMessage());

            // 即使出错也返回基本结构
            overview.put("kafka", new HashMap<>());
            overview.put("flink", new HashMap<>());
            overview.put("clickhouse", new HashMap<>());
            overview.put("redis", new HashMap<>());
        }

        return overview;
    }

    /**
     * 获取实时吞吐量数据
     * <p>
     * 获取最近60秒的吞吐量历史数据，用于前端绘制实时吞吐量图表。
     * <p>
     * 数据来源：从Redis缓存获取，键格式为monitor:throughput:{timestamp}。
     * <p>
     * 示例请求：{@code GET /api/monitor/throughput}
     *
     * @return 吞吐量数据Map，包含历史数据数组和当前值
     */
    @GetMapping("/throughput")
    public Map<String, Object> getThroughputData() {
        Map<String, Object> data = new HashMap<>();

        try {
            // 从Redis获取最近60秒的吞吐量数据（每秒一个数据点）
            List<Map<String, Object>> throughputHistory = new ArrayList<>();

            long now = System.currentTimeMillis();
            for (int i = 59; i >= 0; i--) {
                long timestamp = now - (i * 1000);
                String key = "monitor:throughput:" + (timestamp / 1000); // 使用秒级时间戳作为键

                Object value = redisTemplate.opsForValue().get(key);

                Map<String, Object> point = new HashMap<>();
                point.put("timestamp", timestamp);
                point.put("value", value != null ? value : 0.0);
                throughputHistory.add(point);
            }

            data.put("history", throughputHistory);
            data.put("current", throughputHistory.isEmpty() ? 0.0
                    : throughputHistory.get(throughputHistory.size() - 1).get("value"));

        } catch (Exception e) {
            log.error("获取吞吐量数据失败：{}", e.getMessage(), e);
            data.put("error", e.getMessage());
        }

        return data;
    }

    /**
     * 更新监控统计
     * <p>
     * 由Flink作业或其他组件调用，用于更新各组件的监控统计数据到Redis。
     * <p>
     * 请求体格式：
     * <pre>
     * {
     *   "component": "kafka|flink|clickhouse|redis",
     *   "metrics": {
     *     "key1": "value1",
     *     "key2": "value2"
     *   }
     * }
     * </pre>
     * <p>
     * 示例请求：{@code POST /api/monitor/update}
     *
     * @param stats 监控统计数据，包含组件名称和指标Map
     * @return 更新结果，包含是否成功和消息
     */
    @PostMapping("/update")
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateMonitorStats(@RequestBody Map<String, Object> stats) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 更新各组件统计数据到Redis（设置5分钟过期时间）
            String component = (String) stats.get("component");
            Map<String, Object> metrics = (Map<String, Object>) stats.get("metrics");

            if (component != null && metrics != null) {
                for (Map.Entry<String, Object> entry : metrics.entrySet()) {
                    String key = "monitor:" + component + ":" + entry.getKey();
                    redisTemplate.opsForValue().set(key, entry.getValue(), 5, TimeUnit.MINUTES);
                }

                result.put("success", true);
                result.put("message", "监控统计信息更新成功");
            } else {
                result.put("success", false);
                result.put("message", "统计信息格式无效");
            }

        } catch (Exception e) {
            log.error("更新监控统计信息失败：{}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * 获取系统运行时间
     * <p>
     * 计算系统从启动到现在的运行时间。
     * <p>
     * 数据来源：从Redis获取系统启动时间戳，与当前时间计算差值。
     *
     * @return 系统运行时间字符串，格式：Xh Ym（如：2h 30m）
     */
    @SuppressWarnings("unused")
    private String getUptime() {
        // 从Redis获取启动时间
        Object startTime = redisTemplate.opsForValue().get("monitor:system:start_time");
        if (startTime == null) {
            return "Unknown";
        }

        // 计算运行时间（当前时间 - 启动时间）
        long uptime = System.currentTimeMillis() - ((Number) startTime).longValue();
        long hours = TimeUnit.MILLISECONDS.toHours(uptime);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60;

        return String.format("%dh %dm", hours, minutes);
    }
}
