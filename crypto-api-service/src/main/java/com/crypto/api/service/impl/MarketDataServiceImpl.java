package com.crypto.api.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.crypto.api.dto.MarketDataDTO;
import com.crypto.api.entity.SymbolConfig;
import com.crypto.api.repository.ClickHouseRepository;
import com.crypto.api.repository.SymbolConfigMapper;
import com.crypto.api.service.MarketDataService;
import com.crypto.common.constants.RedisKeys;
import com.crypto.common.entity.KlineData;

import io.micrometer.common.lang.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.Arrays;

/**
 * 市场数据服务实现
 * <p>
 * 负责聚合 ClickHouse (历史数据)、Redis (实时缓存) 和 OKX API (降级兜底) 的数据。
 * </p>
 * 
 * <h3>数据获取策略：</h3>
 * <ol>
 * <li><strong>实时价格</strong>: Redis &rarr; ClickHouse &rarr; OKX API</li>
 * <li><strong>K 线数据</strong>: ClickHouse &rarr; OKX API</li>
 * </ol>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class MarketDataServiceImpl implements MarketDataService {

    private final ClickHouseRepository clickHouseRepository;
    private final SymbolConfigMapper symbolConfigMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RestTemplate restTemplate;

    public MarketDataServiceImpl(ClickHouseRepository clickHouseRepository,
            SymbolConfigMapper symbolConfigMapper,
            RedisTemplate<String, Object> redisTemplate,
            RestTemplate restTemplate) {
        this.clickHouseRepository = clickHouseRepository;
        this.symbolConfigMapper = symbolConfigMapper;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
    }

    /**
     * 获取实时价格
     * <p>
     * 优先查询 Redis 缓存 (30s 有效期)。未命中时查询 ClickHouse 最新 K 线。
     * </p>
     * 
     * @param symbol 交易对
     * @return 价格信息
     */
    @Override
    @Nullable
    public MarketDataDTO.PriceData getRealtimePrice(String symbol) {
        // 优先从Redis缓存获取（30秒有效期）
        String cacheKey = RedisKeys.buildPriceKey(symbol);
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            log.debug("从缓存获取价格数据：交易对={}", symbol);
            // RedisTemplate已使用Jackson2JsonRedisSerializer反序列化，直接类型转换
            if (cached instanceof MarketDataDTO.PriceData) {
                return (MarketDataDTO.PriceData) cached;
            }
            // 兼容处理：如果反序列化为Map，则转换为对象
            String jsonStr = JSON.toJSONString(cached);
            return JSON.parseObject(jsonStr, MarketDataDTO.PriceData.class);
        }

        // 缓存未命中，从ClickHouse查询最新K线（1分钟周期）
        KlineData latestKline = clickHouseRepository.queryLatestKline(symbol, "1m");
        if (latestKline == null) {
            log.warn("未找到K线数据：交易对={}", symbol);
            return null;
        }

        // 构建价格数据：使用K线的收盘价作为当前价格
        MarketDataDTO.PriceData priceData = MarketDataDTO.PriceData.builder()
                .symbol(symbol)
                .price(latestKline.getClose())
                .changePercent(latestKline.calculateChangePercent())
                .updateTime(latestKline.getCloseTime())
                .build();

        // 缓存30秒，提高后续查询性能
        redisTemplate.opsForValue().set(cacheKey, priceData, 30, TimeUnit.SECONDS);

        return priceData;
    }

    /**
     * 查询 K 线数据
     * <p>
     * 支持多种时间周期。数据库只存储1分钟K线，其他周期通过实时聚合生成。
     * </p>
     * 
     * @param request 查询参数
     * @return K 线列表
     */
    @Override
    public MarketDataDTO.KlineResponse getKlineData(MarketDataDTO.KlineQueryRequest request) {
        if (request.getSymbol() == null || request.getInterval() == null) {
            throw new IllegalArgumentException("Symbol and interval are required");
        }

        String interval = request.getInterval();
        // 设置默认限制，最多返回1000条
        Integer limit = request.getLimit();
        if (limit == null || limit <= 0) {
            limit = 100;
        }
        if (limit > 1000) {
            limit = 1000;
        }

        List<KlineData> klineDataList;

        // 判断是否需要聚合
        if ("1m".equals(interval)) {
            // 1分钟数据直接查询
            klineDataList = clickHouseRepository.queryKlineData(
                    request.getSymbol(),
                    "1m",
                    request.getStartTime(),
                    request.getEndTime(),
                    limit);
        } else {
            // 其他周期需要从1分钟数据聚合
            klineDataList = getAggregatedKlineData(
                    request.getSymbol(),
                    interval,
                    request.getStartTime(),
                    request.getEndTime(),
                    limit);
        }

        // 转换为DTO
        List<MarketDataDTO.KlineData> klineList = klineDataList.stream()
                .map(this::convertToKlineDTO)
                .collect(Collectors.toList());

        return MarketDataDTO.KlineResponse.builder()
                .symbol(request.getSymbol())
                .interval(interval)
                .data(klineList)
                .count(klineList.size())
                .build();
    }

    /**
     * 从1分钟K线数据聚合成目标周期
     * <p>
     * 将多根1分钟K线合并为一根目标周期K线，计算OHLCV值。
     * </p>
     *
     * @param symbol    交易对
     * @param interval  目标周期 (5m, 15m, 1h, 1d)
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @param limit     限制数量
     * @return 聚合后的K线列表
     */
    private List<KlineData> getAggregatedKlineData(String symbol, String interval,
            LocalDateTime startTime, LocalDateTime endTime, Integer limit) {

        // 计算需要多少根1分钟K线来聚合
        int minutesPerBar = getMinutesPerBar(interval);
        if (minutesPerBar <= 0) {
            log.warn("不支持的K线周期: {}", interval);
            return Collections.emptyList();
        }

        // 查询足够的1分钟数据用于聚合
        int minuteLimit = limit * minutesPerBar + minutesPerBar; // 多取一些确保数据完整
        List<KlineData> minuteData = clickHouseRepository.queryKlineData(
                symbol, "1m", startTime, endTime, minuteLimit);

        if (minuteData.isEmpty()) {
            log.debug("没有1分钟K线数据可供聚合: symbol={}", symbol);
            return Collections.emptyList();
        }

        // 按时间升序排列（数据库返回的是降序）
        minuteData.sort(Comparator.comparing(KlineData::getOpenTime));

        // 按目标周期分组聚合
        List<KlineData> aggregatedList = new ArrayList<>();

        for (int i = 0; i < minuteData.size(); i += minutesPerBar) {
            int endIdx = Math.min(i + minutesPerBar, minuteData.size());
            List<KlineData> group = minuteData.subList(i, endIdx);

            // 如果分组数据不足，跳过（确保K线完整）
            if (group.size() < minutesPerBar && i + minutesPerBar < minuteData.size()) {
                continue;
            }

            KlineData aggregated = aggregateKlineGroup(group, interval);
            if (aggregated != null) {
                aggregatedList.add(aggregated);
            }
        }

        // 按时间降序排列（与原始逻辑一致），并限制数量
        aggregatedList.sort(Comparator.comparing(KlineData::getOpenTime).reversed());
        if (aggregatedList.size() > limit) {
            aggregatedList = aggregatedList.subList(0, limit);
        }

        log.debug("K线聚合完成: symbol={}, interval={}, 原始数据={}条, 聚合后={}条",
                symbol, interval, minuteData.size(), aggregatedList.size());

        return aggregatedList;
    }

    /**
     * 将一组1分钟K线聚合为一根目标周期K线
     */
    private KlineData aggregateKlineGroup(List<KlineData> group, String interval) {
        if (group == null || group.isEmpty()) {
            return null;
        }

        KlineData first = group.get(0);
        KlineData last = group.get(group.size() - 1);

        // OHLCV 计算
        BigDecimal open = first.getOpen();
        BigDecimal close = last.getClose();
        BigDecimal high = group.stream()
                .map(KlineData::getHigh)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal low = group.stream()
                .map(KlineData::getLow)
                .min(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
        BigDecimal volume = group.stream()
                .map(KlineData::getVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal quoteVolume = group.stream()
                .map(KlineData::getQuoteVolume)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int trades = group.stream()
                .mapToInt(KlineData::getTrades)
                .sum();

        return KlineData.builder()
                .symbol(first.getSymbol())
                .interval(interval)
                .openTime(first.getOpenTime())
                .closeTime(last.getCloseTime())
                .open(open)
                .high(high)
                .low(low)
                .close(close)
                .volume(volume)
                .quoteVolume(quoteVolume)
                .trades(trades)
                .insertTime(LocalDateTime.now())
                .build();
    }

    /**
     * 获取每根K线包含的分钟数
     */
    private int getMinutesPerBar(String interval) {
        return switch (interval) {
            case "1m" -> 1;
            case "5m" -> 5;
            case "15m" -> 15;
            case "30m" -> 30;
            case "1h" -> 60;
            case "4h" -> 240;
            case "1d" -> 1440;
            default -> 0;
        };
    }

    @Override
    public List<MarketDataDTO.SymbolInfo> getAllSymbols() {
        List<SymbolConfig> configs = symbolConfigMapper.selectList(null);
        return configs.stream()
                .map(this::convertToSymbolInfo)
                .collect(Collectors.toList());
    }

    @Override
    public List<MarketDataDTO.SymbolInfo> getEnabledSymbols() {
        QueryWrapper<SymbolConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("is_active", 1).eq("collect_enabled", 1);
        List<SymbolConfig> configs = symbolConfigMapper.selectList(wrapper);
        return configs.stream()
                .map(this::convertToSymbolInfo)
                .collect(Collectors.toList());
    }

    @Override
    public MarketDataDTO.MarketStatistics getMarketStatistics() {
        QueryWrapper<SymbolConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("is_active", 1).eq("collect_enabled", 1);
        List<SymbolConfig> enabledSymbols = symbolConfigMapper.selectList(wrapper);

        int totalSymbols = enabledSymbols.size();
        int risingCount = 0;
        int fallingCount = 0;
        int flatCount = 0;

        BigDecimal totalVolume = BigDecimal.ZERO;
        BigDecimal totalChangePercent = BigDecimal.ZERO;

        MarketDataDTO.SymbolInfo topGainer = null;
        MarketDataDTO.SymbolInfo topLoser = null;
        BigDecimal maxGain = BigDecimal.valueOf(-1000);
        BigDecimal maxLoss = BigDecimal.valueOf(1000);

        // 统计各个交易对的数据
        for (SymbolConfig config : enabledSymbols) {
            try {
                Map<String, Object> stats = clickHouseRepository.query24hStatistics(config.getSymbol());
                if (stats == null || stats.isEmpty()) {
                    continue;
                }

                MarketDataDTO.PriceData priceData = getRealtimePrice(config.getSymbol());
                if (priceData == null) {
                    continue;
                }

                BigDecimal changePercent = priceData.getChangePercent();
                if (changePercent != null) {
                    totalChangePercent = totalChangePercent.add(changePercent);

                    if (changePercent.compareTo(BigDecimal.ZERO) > 0) {
                        risingCount++;
                        if (changePercent.compareTo(maxGain) > 0) {
                            maxGain = changePercent;
                            topGainer = convertToSymbolInfoWithPrice(config, priceData, stats);
                        }
                    } else if (changePercent.compareTo(BigDecimal.ZERO) < 0) {
                        fallingCount++;
                        if (changePercent.compareTo(maxLoss) < 0) {
                            maxLoss = changePercent;
                            topLoser = convertToSymbolInfoWithPrice(config, priceData, stats);
                        }
                    } else {
                        flatCount++;
                    }
                }

                // 累加24小时成交量
                Object volume24h = stats.get("volume_24h");
                if (volume24h != null) {
                    totalVolume = totalVolume.add(new BigDecimal(volume24h.toString()));
                }

            } catch (Exception e) {
                log.warn("获取统计数据失败：交易对={}，错误={}", config.getSymbol(), e.getMessage());
            }
        }

        // 计算平均涨跌幅
        BigDecimal avgChangePercent = totalSymbols > 0
                ? totalChangePercent.divide(BigDecimal.valueOf(totalSymbols), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return MarketDataDTO.MarketStatistics.builder()
                .totalSymbols(totalSymbols)
                .risingCount(risingCount)
                .fallingCount(fallingCount)
                .flatCount(flatCount)
                .totalVolume24h(totalVolume)
                .avgChangePercent(avgChangePercent)
                .topGainer(topGainer)
                .topLoser(topLoser)
                .statisticsTime(LocalDateTime.now())
                .build();
    }

    @Override
    public MarketDataDTO get24hMarketData(String symbol) {
        // 获取24小时统计数据
        Map<String, Object> stats = clickHouseRepository.query24hStatistics(symbol);

        // 如果ClickHouse无数据，返回null以触发fallback逻辑
        if (stats == null || stats.isEmpty()) {
            log.warn("未找到K线数据：交易对={}", symbol);
            return null;
        }

        // 获取实时价格
        MarketDataDTO.PriceData priceData = getRealtimePrice(symbol);
        if (priceData == null) {
            return null;
        }

        return MarketDataDTO.builder()
                .symbol(symbol)
                .price(priceData.getPrice())
                .change24h(priceData.getChangePercent())
                .high24h(getBigDecimalFromMap(stats, "high_24h"))
                .low24h(getBigDecimalFromMap(stats, "low_24h"))
                .volume24h(getBigDecimalFromMap(stats, "volume_24h"))
                .quoteVolume24h(getBigDecimalFromMap(stats, "quote_volume_24h"))
                .timestamp(priceData.getUpdateTime())
                .build();
    }

    @Override
    public void refreshPriceCache(String symbol) {
        KlineData latestKline = clickHouseRepository.queryLatestKline(symbol, "1m");
        if (latestKline != null) {
            MarketDataDTO.PriceData priceData = MarketDataDTO.PriceData.builder()
                    .symbol(symbol)
                    .price(latestKline.getClose())
                    .changePercent(latestKline.calculateChangePercent())
                    .updateTime(latestKline.getCloseTime())
                    .build();

            String cacheKey = RedisKeys.buildPriceKey(symbol);
            redisTemplate.opsForValue().set(cacheKey, priceData, 30, TimeUnit.SECONDS);
            log.debug("刷新价格缓存：交易对={}", symbol);
        }
    }

    @Override
    public void batchRefreshPriceCache(List<String> symbols) {
        for (String symbol : symbols) {
            try {
                refreshPriceCache(symbol);
            } catch (Exception e) {
                log.warn("刷新价格缓存失败：交易对={}，错误={}", symbol, e.getMessage());
            }
        }
    }

    /**
     * 转换KlineData为DTO
     */
    private MarketDataDTO.KlineData convertToKlineDTO(KlineData klineData) {
        return MarketDataDTO.KlineData.builder()
                .openTime(klineData.getOpenTime())
                .open(klineData.getOpen() != null ? klineData.getOpen().toPlainString() : "0.00")
                .high(klineData.getHigh() != null ? klineData.getHigh().toPlainString() : "0.00")
                .low(klineData.getLow() != null ? klineData.getLow().toPlainString() : "0.00")
                .close(klineData.getClose() != null ? klineData.getClose().toPlainString() : "0.00")
                .volume(klineData.getVolume() != null ? klineData.getVolume().toPlainString() : "0.00")
                .quoteVolume(klineData.getQuoteVolume() != null ? klineData.getQuoteVolume().toPlainString() : "0.00")
                .trades(klineData.getTrades())
                .build();
    }

    /**
     * 转换SymbolConfig为SymbolInfo
     */
    private MarketDataDTO.SymbolInfo convertToSymbolInfo(SymbolConfig config) {
        MarketDataDTO.PriceData priceData = getRealtimePrice(config.getSymbol());
        Map<String, Object> stats = clickHouseRepository.query24hStatistics(config.getSymbol());

        return MarketDataDTO.SymbolInfo.builder()
                .symbol(config.getSymbol())
                .baseAsset(config.getBaseCurrency())
                .quoteAsset(config.getQuoteCurrency())
                .status("TRADING") // 默认状态
                .enabled(config.getIsActive())
                .lastPrice(priceData != null ? priceData.getPrice() : null)
                .change24h(priceData != null ? priceData.getChangePercent() : null)
                .volume24h(getBigDecimalFromMap(stats, "volume_24h"))
                .high24h(getBigDecimalFromMap(stats, "high_24h"))
                .low24h(getBigDecimalFromMap(stats, "low_24h"))
                .build();
    }

    /**
     * 转换SymbolConfig为SymbolInfo(带价格和统计数据)
     */
    private MarketDataDTO.SymbolInfo convertToSymbolInfoWithPrice(SymbolConfig config,
            MarketDataDTO.PriceData priceData,
            Map<String, Object> stats) {
        return MarketDataDTO.SymbolInfo.builder()
                .symbol(config.getSymbol())
                .baseAsset(config.getBaseCurrency())
                .quoteAsset(config.getQuoteCurrency())
                .status("TRADING")
                .enabled(config.getIsActive())
                .lastPrice(priceData.getPrice())
                .change24h(priceData.getChangePercent())
                .volume24h(getBigDecimalFromMap(stats, "volume_24h"))
                .high24h(getBigDecimalFromMap(stats, "high_24h"))
                .low24h(getBigDecimalFromMap(stats, "low_24h"))
                .build();
    }

    @Override
    public List<MarketDataDTO> getMarketDataList() {
        List<MarketDataDTO.SymbolInfo> symbolInfos = getEnabledSymbols();

        // 如果没有启用的交易对，使用默认的4个交易对
        if (symbolInfos.isEmpty()) {
            symbolInfos = Arrays.asList(
                    MarketDataDTO.SymbolInfo.builder().symbol("BTC-USDT").baseAsset("BTC").quoteAsset("USDT")
                            .enabled(true).build(),
                    MarketDataDTO.SymbolInfo.builder().symbol("ETH-USDT").baseAsset("ETH").quoteAsset("USDT")
                            .enabled(true).build(),
                    MarketDataDTO.SymbolInfo.builder().symbol("BNB-USDT").baseAsset("BNB").quoteAsset("USDT")
                            .enabled(true).build(),
                    MarketDataDTO.SymbolInfo.builder().symbol("SOL-USDT").baseAsset("SOL").quoteAsset("USDT")
                            .enabled(true).build());
        }

        // 使用并行流并发获取数据，显著提升性能
        return symbolInfos.parallelStream().map(symbolInfo -> {
            try {
                // 从ClickHouse获取24h数据
                MarketDataDTO marketData = get24hMarketData(symbolInfo.getSymbol());

                // 如果ClickHouse没有数据，尝试从OKX API获取
                if (marketData == null) {
                    marketData = fetchFromOkxApi(symbolInfo.getSymbol());
                }

                // 如果还是没有数据，创建默认数据占位符
                if (marketData == null) {
                    // log.warn("No data available for {}, creating placeholder",
                    // symbolInfo.getSymbol());
                    marketData = MarketDataDTO.builder()
                            .symbol(symbolInfo.getSymbol())
                            .price(BigDecimal.ZERO)
                            .change24h(BigDecimal.ZERO)
                            .high24h(BigDecimal.ZERO)
                            .low24h(BigDecimal.ZERO)
                            .volume24h(BigDecimal.ZERO)
                            .quoteVolume24h(BigDecimal.ZERO)
                            .timestamp(LocalDateTime.now())
                            .build();
                }
                return marketData;
            } catch (Exception e) {
                log.error("获取市场数据失败：交易对={}，错误={}", symbolInfo.getSymbol(), e.getMessage());
                // 出错时返回占位符
                return MarketDataDTO.builder()
                        .symbol(symbolInfo.getSymbol())
                        .price(BigDecimal.ZERO)
                        .change24h(BigDecimal.ZERO)
                        .high24h(BigDecimal.ZERO)
                        .low24h(BigDecimal.ZERO)
                        .volume24h(BigDecimal.ZERO)
                        .quoteVolume24h(BigDecimal.ZERO)
                        .timestamp(LocalDateTime.now())
                        .build();
            }
        }).collect(Collectors.toList());
    }

    /**
     * 从OKX API获取实时价格数据（作为fallback）
     */
    private MarketDataDTO fetchFromOkxApi(String symbol) {
        try {
            // 统一符号格式: 确保是 BTC-USDT 格式
            String instId = symbol.contains("-") ? symbol : symbol.replace("USDT", "-USDT");

            // 尝试从Redis缓存获取OKX数据
            String cacheKey = "okx:ticker:" + instId;
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    MarketDataDTO cachedData = JSON.parseObject(JSON.toJSONString(cached), MarketDataDTO.class);
                    if (cachedData != null && cachedData.getPrice() != null
                            && cachedData.getPrice().compareTo(BigDecimal.ZERO) > 0) {
                        return cachedData;
                    }
                }
            } catch (Exception e) {
                // ignore cache errors
            }

            // 使用注入的 RestTemplate，而不是每次 new 一个
            String url = "https://www.okx.com/api/v5/market/ticker?instId=" + instId;

            String response = restTemplate.getForObject(url, String.class);
            if (response == null || response.isEmpty()) {
                return null;
            }

            JSONObject jsonResponse = JSON.parseObject(response);
            if (jsonResponse == null || !"0".equals(jsonResponse.getString("code"))) {
                return null;
            }

            JSONArray dataArray = jsonResponse.getJSONArray("data");
            if (dataArray == null || dataArray.isEmpty()) {
                return null;
            }

            JSONObject ticker = dataArray.getJSONObject(0);
            if (ticker == null) {
                return null;
            }

            BigDecimal lastPrice = new BigDecimal(ticker.getString("last"));
            BigDecimal open24h = new BigDecimal(ticker.getString("open24h"));
            BigDecimal high24h = new BigDecimal(ticker.getString("high24h"));
            BigDecimal low24h = new BigDecimal(ticker.getString("low24h"));
            BigDecimal vol24h = new BigDecimal(ticker.getString("vol24h"));
            BigDecimal volCcy24h = new BigDecimal(ticker.getString("volCcy24h"));

            // 计算24h涨跌幅
            BigDecimal change24h = BigDecimal.ZERO;
            if (open24h.compareTo(BigDecimal.ZERO) > 0) {
                change24h = lastPrice.subtract(open24h)
                        .divide(open24h, 4, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100"));
            }

            MarketDataDTO marketData = MarketDataDTO.builder()
                    .symbol(symbol)
                    .price(lastPrice)
                    .change24h(change24h)
                    .high24h(high24h)
                    .low24h(low24h)
                    .volume24h(vol24h)
                    .quoteVolume24h(volCcy24h)
                    .timestamp(LocalDateTime.now())
                    .build();

            // 缓存30秒
            try {
                redisTemplate.opsForValue().set(cacheKey, marketData, 30, TimeUnit.SECONDS);
            } catch (Exception e) {
                // ignore
            }

            return marketData;

        } catch (Exception e) {
            log.warn("从OKX API获取数据失败：交易对={}，错误={}", symbol, e.getMessage());
            return null;
        }
    }

    @Override
    public Map<String, Object> getMarketOverview() {
        Map<String, Object> overview = new LinkedHashMap<>();
        MarketDataDTO.MarketStatistics stats = getMarketStatistics();

        // 基础统计
        overview.put("totalSymbols", stats.getTotalSymbols());
        overview.put("risingCount", stats.getRisingCount());
        overview.put("fallingCount", stats.getFallingCount());
        overview.put("flatCount", stats.getFlatCount());
        overview.put("totalVolume24h", stats.getTotalVolume24h());
        overview.put("avgChangePercent", stats.getAvgChangePercent());

        // 最大涨幅和跌幅
        Map<String, Object> topMovers = new LinkedHashMap<>();
        if (stats.getTopGainer() != null) {
            topMovers.put("topGainer", stats.getTopGainer());
        }
        if (stats.getTopLoser() != null) {
            topMovers.put("topLoser", stats.getTopLoser());
        }
        overview.put("topMovers", topMovers);
        overview.put("statisticsTime", stats.getStatisticsTime());

        return overview;
    }

    /**
     * 从Map中获取BigDecimal值
     */
    private BigDecimal getBigDecimalFromMap(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return new BigDecimal(value.toString());
    }
}
