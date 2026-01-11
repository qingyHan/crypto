package com.crypto.api.service;

import com.crypto.api.entity.StrategyConfig;
import com.crypto.common.entity.AlertRecord;
import com.crypto.common.enums.AlertSeverity;
import com.crypto.common.enums.AlertType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 策略监控服务 - 用户自定义策略的实时监控引擎
 * 
 * 本服务是系统的重要组件，实现了用户在前端配置的预警策略的实时监控。
 * 与Flink的预警引擎不同，本服务直接从OKX API获取实时价格，根据用户自定义的
 * 阈值和时间窗口进行检测，确保用户配置的策略能够被准确执行。
 * 
 * 核心功能：
 * 1. 策略读取：每10秒从MySQL数据库读取所有启用的策略配置
 * 2. 价格获取：从OKX REST API获取实时市场价格（支持多币种）
 * 3. 历史缓存：维护价格历史缓存（保留2小时数据），用于计算涨跌幅
 * 4. 策略检测：根据用户配置的阈值和时间窗口检测价格异常
 * 5. 预警生成：满足条件时生成预警记录并保存到数据库
 * 6. 冷却机制：同一策略对同一币种，5分钟内只触发一次，避免重复预警
 * 
 * 支持的策略类型：
 * - PRICE_SPIKE（价格暴涨）：检测指定时间窗口内价格上涨超过阈值
 * - PRICE_DROP（价格暴跌）：检测指定时间窗口内价格下跌超过阈值
 * - VOLUME_ANOMALY（交易量异常）：暂未实现，需要额外数据源
 * - WHALE_TRADE（巨鲸交易）：暂未实现，需要实时交易流数据
 * 
 * 工作流程：
 * 定时任务（@Scheduled，每10秒） → 读取启用的策略列表（从MySQL） → 
 * 获取当前市场价格（从OKX API） → 更新价格历史缓存 → 遍历每个策略：
 * 获取策略适用的币种列表 → 对每个币种：获取历史价格（根据策略的timeWindow参数） → 
 * 计算涨跌幅 = (当前价格 - 历史价格) / 历史价格 * 100 → 判断是否超过阈值 → 
 * 检查冷却期（避免重复预警） → 生成预警记录 → 保存预警到MySQL → 
 * 更新策略的触发次数和最后触发时间
 * 
 * 配置参数说明：
 * - threshold（阈值）：价格变化百分比，例如0.1表示0.1%，5表示5%
 * - timeWindow（时间窗口）：检测的时间范围（单位：分钟），例如1表示1分钟，60表示1小时
 * - symbols（交易对）：策略适用的币种，"*"表示所有支持的币种
 * 
 * 价格历史缓存机制：
 * - 使用ConcurrentHashMap存储每个币种的价格历史（线程安全）
 * - 每个价格点包含：时间戳（毫秒）、价格（BigDecimal）
 * - 自动清理超过2小时的历史数据，节省内存
 * - 支持根据时间窗口查找历史价格（用于计算涨跌幅）
 * 
 * 与Flink预警引擎的区别：
 * 特性对比：
 * - 数据源：Flink预警引擎使用Kafka交易流，策略监控服务使用OKX REST API
 * - 配置来源：Flink预警引擎使用硬编码阈值，策略监控服务使用用户自定义配置
 * - 检测频率：Flink预警引擎是实时（毫秒级），策略监控服务是定时（10秒）
 * - 适用场景：Flink预警引擎适用于系统级预警（3%等固定规则），策略监控服务适用于用户个性化预警（0.1%等自定义规则）
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class StrategyMonitorService {

    private final StrategyService strategyService;
    private final AlertService alertService;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    // 价格历史缓存（用于计算涨跌幅）
    private final Map<String, LinkedList<PricePoint>> priceHistory = new ConcurrentHashMap<>();
    
    // 预警冷却期（避免频繁触发同一策略）
    private final Map<String, LocalDateTime> alertCooldown = new ConcurrentHashMap<>();
    private static final int COOLDOWN_MINUTES = 5;
    
    // 支持的币种
    private static final List<String> SUPPORTED_SYMBOLS = Arrays.asList(
        "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT", "XRPUSDT", "ADAUSDT", "DOGEUSDT"
    );

    public StrategyMonitorService(StrategyService strategyService, AlertService alertService) {
        this.strategyService = strategyService;
        this.alertService = alertService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        
        log.info("StrategyMonitorService initialized - will check strategies every 10 seconds");
    }

    /**
     * 定时检测策略（每10秒执行一次）
     * 
     * 这是服务的核心方法，通过Spring的@Scheduled注解实现定时执行。
     * 执行频率为每10秒一次，这是一个平衡实时性和系统负载的折中方案。
     * 
     * 执行流程：
     * 1. 从数据库查询所有启用的策略配置（enabled=true）
     * 2. 如果没有任何策略，直接返回（节省资源）
     * 3. 批量获取所有支持币种的当前价格（从OKX API）
     * 4. 更新价格历史缓存（用于后续计算涨跌幅）
     * 5. 遍历每个策略，调用checkStrategy进行检测
     * 6. 异常处理：单个策略检测失败不影响其他策略
     * 
     * 性能优化：
     * - 批量获取价格，减少API调用次数
     * - 使用价格历史缓存，避免重复计算
     * - 异常隔离，单个策略失败不影响整体
     */
    @Scheduled(fixedRate = 10000)
    public void monitorStrategies() {
        try {
            // 1. 获取所有启用的策略
            List<StrategyConfig> enabledStrategies = strategyService.getAllEnabledStrategies();
            if (enabledStrategies.isEmpty()) {
                return;
            }
            
            // 2. 获取当前市场数据
            Map<String, BigDecimal> currentPrices = fetchCurrentPrices();
            if (currentPrices.isEmpty()) {
                log.warn("Failed to fetch current prices");
                return;
            }
            
            // 3. 更新价格历史
            updatePriceHistory(currentPrices);
            
            // 4. 检测每个策略
            for (StrategyConfig strategy : enabledStrategies) {
                try {
                    checkStrategy(strategy, currentPrices);
                } catch (Exception e) {
                    log.error("Error checking strategy {}: {}", strategy.getId(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("Strategy monitoring error: {}", e.getMessage());
        }
    }

    /**
     * 检测单个策略
     */
    private void checkStrategy(StrategyConfig strategy, Map<String, BigDecimal> currentPrices) {
        String strategyType = strategy.getStrategyType();
        Map<String, Object> params = strategy.getParameters();
        
        if (params == null) {
            return;
        }
        
        // 获取策略适用的币种
        List<String> symbols = getStrategySymbols(strategy);
        
        for (String symbol : symbols) {
            BigDecimal currentPrice = currentPrices.get(symbol);
            if (currentPrice == null) {
                continue;
            }
            
            AlertRecord alert = null;
            
            switch (strategyType) {
                case "PRICE_SPIKE":
                    alert = checkPriceSpike(strategy, symbol, currentPrice, params);
                    break;
                case "PRICE_DROP":
                    alert = checkPriceDrop(strategy, symbol, currentPrice, params);
                    break;
                case "VOLUME_ANOMALY":
                    // 交易量检测需要额外数据，暂时跳过
                    break;
                case "WHALE_TRADE":
                    // 巨鲸交易需要额外数据，暂时跳过
                    break;
                default:
                    log.debug("Unknown strategy type: {}", strategyType);
            }
            
            if (alert != null) {
                saveAndNotifyAlert(alert, strategy);
            }
        }
    }

    /**
     * 检测价格暴涨策略 - 价格上涨异常检测
     * 
     * 检测指定时间窗口内，价格上涨是否超过用户配置的阈值。
     * 
     * 检测逻辑：
     * 1. 从策略参数中读取阈值（threshold）和时间窗口（timeWindow）
     * 2. 检查冷却期：同一策略对同一币种，5分钟内只触发一次
     * 3. 从价格历史缓存中获取timeWindow分钟前的历史价格
     * 4. 计算涨幅 = (当前价格 - 历史价格) / 历史价格 * 100
     * 5. 判断：如果涨幅 >= 阈值，则触发预警
     * 6. 生成AlertRecord对象，包含预警详细信息
     * 7. 设置冷却期，防止短时间内重复触发
     * 
     * 示例：
     * 策略配置：threshold=0.1, timeWindow=1（1分钟内涨幅超过0.1%）
     * 当前价格：92500，1分钟前价格：92400
     * 涨幅 = (92500-92400)/92400*100 = 0.108% > 0.1%
     * 结果：触发预警
     * 
     * @param strategy 策略配置对象，包含策略ID、名称、参数等
     * @param symbol 交易对符号，如"BTCUSDT"
     * @param currentPrice 当前实时价格
     * @param params 策略参数Map，包含threshold和timeWindow
     * @return 如果触发预警，返回AlertRecord对象；否则返回null
     */
    private AlertRecord checkPriceSpike(StrategyConfig strategy, String symbol, 
                                        BigDecimal currentPrice, Map<String, Object> params) {
        double threshold = getDoubleParam(params, "threshold", 5.0);
        int timeWindow = getIntParam(params, "timeWindow", 60); // 分钟
        
        // 检查冷却期
        String cooldownKey = strategy.getId() + "_" + symbol + "_SPIKE";
        if (isInCooldown(cooldownKey)) {
            return null;
        }
        
        // 获取历史价格
        BigDecimal oldPrice = getHistoricalPrice(symbol, timeWindow);
        if (oldPrice == null || oldPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        // 计算涨幅
        BigDecimal change = currentPrice.subtract(oldPrice)
            .divide(oldPrice, 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        // 检查是否超过阈值（正向涨幅）
        if (change.doubleValue() >= threshold) {
            setCooldown(cooldownKey);
            
            return AlertRecord.builder()
                .alertId(AlertRecord.generateAlertId())
                .alertType(AlertType.PRICE_SPIKE)
                .symbol(symbol)
                .severity(AlertSeverity.fromChangePercent(change.doubleValue()))
                .triggerTime(LocalDateTime.now())
                .currentPrice(currentPrice)
                .changePercent(change)
                .message(String.format("[%s] %s价格%d分钟内暴涨%.2f%% (阈值: %.2f%%)", 
                    strategy.getStrategyName(), symbol, timeWindow, change.doubleValue(), threshold))
                .strategyId(strategy.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        }
        
        return null;
    }

    /**
     * 检测价格暴跌策略
     */
    private AlertRecord checkPriceDrop(StrategyConfig strategy, String symbol, 
                                       BigDecimal currentPrice, Map<String, Object> params) {
        double threshold = getDoubleParam(params, "threshold", 3.0);
        int timeWindow = getIntParam(params, "timeWindow", 30); // 分钟
        
        // 检查冷却期
        String cooldownKey = strategy.getId() + "_" + symbol + "_DROP";
        if (isInCooldown(cooldownKey)) {
            return null;
        }
        
        // 获取历史价格
        BigDecimal oldPrice = getHistoricalPrice(symbol, timeWindow);
        if (oldPrice == null || oldPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        
        // 计算跌幅
        BigDecimal change = currentPrice.subtract(oldPrice)
            .divide(oldPrice, 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"));
        
        // 检查是否超过阈值（负向跌幅）
        if (change.doubleValue() <= -threshold) {
            setCooldown(cooldownKey);
            
            return AlertRecord.builder()
                .alertId(AlertRecord.generateAlertId())
                .alertType(AlertType.PRICE_DROP)
                .symbol(symbol)
                .severity(AlertSeverity.fromChangePercent(Math.abs(change.doubleValue())))
                .triggerTime(LocalDateTime.now())
                .currentPrice(currentPrice)
                .changePercent(change)
                .message(String.format("[%s] %s价格%d分钟内暴跌%.2f%% (阈值: -%.2f%%)", 
                    strategy.getStrategyName(), symbol, timeWindow, Math.abs(change.doubleValue()), threshold))
                .strategyId(strategy.getId())
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        }
        
        return null;
    }

    /**
     * 从OKX获取当前价格
     */
    private Map<String, BigDecimal> fetchCurrentPrices() {
        Map<String, BigDecimal> prices = new HashMap<>();
        
        try {
            // 使用OKX的ticker API获取所有支持币种的价格
            for (String symbol : SUPPORTED_SYMBOLS) {
                try {
                    String instId = symbol.replace("USDT", "-USDT");
                    String url = "https://www.okx.com/api/v5/market/ticker?instId=" + instId;
                    
                    HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(5))
                        .GET()
                        .build();
                    
                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                    
                    if (response.statusCode() == 200) {
                        JsonNode root = objectMapper.readTree(response.body());
                        if ("0".equals(root.get("code").asText()) && root.has("data") && root.get("data").size() > 0) {
                            String lastPrice = root.get("data").get(0).get("last").asText();
                            prices.put(symbol, new BigDecimal(lastPrice));
                        }
                    }
                } catch (Exception e) {
                    log.debug("Failed to fetch price for {}: {}", symbol, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error fetching prices from OKX: {}", e.getMessage());
        }
        
        return prices;
    }

    /**
     * 更新价格历史
     */
    private void updatePriceHistory(Map<String, BigDecimal> currentPrices) {
        long now = System.currentTimeMillis();
        
        for (Map.Entry<String, BigDecimal> entry : currentPrices.entrySet()) {
            String symbol = entry.getKey();
            BigDecimal price = entry.getValue();
            
            LinkedList<PricePoint> history = priceHistory.computeIfAbsent(symbol, k -> new LinkedList<>());
            history.addLast(new PricePoint(now, price));
            
            // 保留最近2小时的数据
            long cutoff = now - 2 * 60 * 60 * 1000;
            while (!history.isEmpty() && history.getFirst().timestamp < cutoff) {
                history.removeFirst();
            }
        }
    }

    /**
     * 获取历史价格（指定分钟前）
     */
    private BigDecimal getHistoricalPrice(String symbol, int minutesAgo) {
        LinkedList<PricePoint> history = priceHistory.get(symbol);
        if (history == null || history.isEmpty()) {
            return null;
        }
        
        long targetTime = System.currentTimeMillis() - minutesAgo * 60 * 1000;
        
        // 找到最接近目标时间的价格点
        PricePoint closest = null;
        long minDiff = Long.MAX_VALUE;
        
        for (PricePoint point : history) {
            long diff = Math.abs(point.timestamp - targetTime);
            if (diff < minDiff) {
                minDiff = diff;
                closest = point;
            }
        }
        
        // 如果最接近的点在目标时间之后超过1分钟，则认为数据不足
        if (closest == null || closest.timestamp > targetTime + 60 * 1000) {
            return null;
        }
        
        return closest.price;
    }

    /**
     * 获取策略适用的币种列表
     */
    private List<String> getStrategySymbols(StrategyConfig strategy) {
        String symbols = strategy.getSymbols();
        
        if (symbols == null || symbols.isEmpty() || "*".equals(symbols.trim())) {
            return SUPPORTED_SYMBOLS;
        }
        
        List<String> result = new ArrayList<>();
        for (String s : symbols.split(",")) {
            String trimmed = s.trim().toUpperCase();
            if (SUPPORTED_SYMBOLS.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        
        return result.isEmpty() ? SUPPORTED_SYMBOLS : result;
    }

    /**
     * 保存并通知预警
     */
    private void saveAndNotifyAlert(AlertRecord alert, StrategyConfig strategy) {
        try {
            // 保存预警
            boolean saved = alertService.saveAlert(alert);
            
            if (saved) {
                log.info("🚨 策略触发预警: [{}] {} - {}", 
                    strategy.getStrategyName(), alert.getSymbol(), alert.getMessage());
                
                // 更新策略触发次数
                strategyService.updateTriggerInfo(strategy.getId());
            }
        } catch (Exception e) {
            log.error("Failed to save alert: {}", e.getMessage());
        }
    }

    /**
     * 检查是否在冷却期内
     */
    private boolean isInCooldown(String key) {
        LocalDateTime lastAlert = alertCooldown.get(key);
        if (lastAlert == null) {
            return false;
        }
        return LocalDateTime.now().isBefore(lastAlert.plusMinutes(COOLDOWN_MINUTES));
    }

    /**
     * 设置冷却期
     */
    private void setCooldown(String key) {
        alertCooldown.put(key, LocalDateTime.now());
    }

    /**
     * 获取double参数
     */
    private double getDoubleParam(Map<String, Object> params, String key, double defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 获取int参数
     */
    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 价格点
     */
    private static class PricePoint {
        final long timestamp;
        final BigDecimal price;
        
        PricePoint(long timestamp, BigDecimal price) {
            this.timestamp = timestamp;
            this.price = price;
        }
    }
}

