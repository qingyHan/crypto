package com.crypto.api.controller;

import com.crypto.api.dto.ApiResponse;
import com.crypto.api.dto.MarketDataDTO;
import com.crypto.api.service.MarketDataService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 市场数据API控制器
 * <p>
 * 提供市场行情数据查询接口，是前端市场数据展示功能的主要数据源。
 * <p>
 * 主要功能：
 * <ul>
 * <li>实时价格：获取交易对的实时价格数据</li>
 * <li>K线数据：获取历史K线数据，支持多种时间周期</li>
 * <li>市场统计：获取市场整体统计数据</li>
 * <li>交易对管理：查询交易对列表和状态</li>
 * <li>24小时行情：获取24小时涨跌幅、成交量等数据</li>
 * </ul>
 * <p>
 * 数据来源：
 * <ul>
 * <li>K线数据：优先从ClickHouse查询（Flink聚合），无数据时从OKX API获取</li>
 * <li>实时价格：从Redis缓存或OKX API获取</li>
 * <li>市场统计：从Redis缓存获取（由Flink作业定期更新）</li>
 * </ul>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/market")
public class MarketDataController {

    private final MarketDataService marketDataService;

    public MarketDataController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    /**
     * 获取实时价格
     * <p>
     * 获取指定交易对的实时价格数据，包括当前价格、24小时涨跌幅等。
     * <p>
     * 数据来源：优先从Redis缓存获取，缓存未命中时从OKX API获取并更新缓存。
     * <p>
     * 示例请求：{@code GET /api/market/price/BTCUSDT}
     *
     * @param symbol 交易对符号，路径参数，如"BTCUSDT"、"ETHUSDT"
     * @return 实时价格数据，包含价格、涨跌幅、成交量等信息
     */
    @GetMapping("/price/{symbol}")
    public ApiResponse<MarketDataDTO.PriceData> getRealtimePrice(@PathVariable String symbol) {
        
        log.debug("获取实时价格：交易对={}", symbol);

        MarketDataDTO.PriceData priceData = marketDataService.getRealtimePrice(symbol);

        if (priceData == null) {
            return ApiResponse.notFound("Price data not found for symbol: " + symbol);
        }

        return ApiResponse.success(priceData);
    }

    /**
     * 获取 K 线数据
     * <p>
     * 前端 K 线图的核心数据接口。优先查询 ClickHouse，降级时查询 OKX API。
     * </p>
     * 
     * @param symbol    交易对 (如 BTCUSDT)
     * @param interval  周期 (1m, 5m, 1h, 1d 等)
     * @param startTime 开始时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param endTime   结束时间 (格式: yyyy-MM-dd HH:mm:ss)
     * @param limit     条数限制 (默认 100)
     * @return K 线数据列表
     */
    @GetMapping("/kline/{symbol}")
    public ApiResponse<MarketDataDTO.KlineResponse> getKlineData(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "1h") String interval,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "100") Integer limit) {

        log.debug("获取K线数据：交易对={}，周期={}，限制={}", symbol, interval, limit);

        // 构建K线查询请求对象
        MarketDataDTO.KlineQueryRequest request = MarketDataDTO.KlineQueryRequest.builder()
                .symbol(symbol)
                .interval(interval)
                .limit(limit)
                .build();

        // 解析开始时间参数（如果提供）
        if (startTime != null && !startTime.isEmpty()) {
            try {
                request.setStartTime(java.time.LocalDateTime.parse(startTime.replace(" ", "T")));
            } catch (Exception e) {
                return ApiResponse.badRequest("Invalid startTime format, expected: yyyy-MM-dd HH:mm:ss");
            }
        }

        // 解析结束时间参数（如果提供）
        if (endTime != null && !endTime.isEmpty()) {
            try {
                request.setEndTime(java.time.LocalDateTime.parse(endTime.replace(" ", "T")));
            } catch (Exception e) {
                return ApiResponse.badRequest("Invalid endTime format, expected: yyyy-MM-dd HH:mm:ss");
            }
        }

        MarketDataDTO.KlineResponse response = marketDataService.getKlineData(request);

        return ApiResponse.success(response);
    }

    /**
     * 获取交易对列表
     * <p>
     * 获取所有交易对或仅启用的交易对列表，用于前端下拉选择等场景。
     * <p>
     * 示例请求：
     * <ul>
     * <li>{@code GET /api/market/symbols} - 返回所有交易对</li>
     * <li>{@code GET /api/market/symbols?enabled=true} - 仅返回启用的交易对</li>
     * </ul>
     *
     * @param enabled 是否只返回启用的交易对，可选，默认null返回全部
     * @return 交易对列表，包含交易对符号、名称、状态等信息
     */
    @GetMapping("/symbols")
    public ApiResponse<List<MarketDataDTO.SymbolInfo>> getSymbols(
            @RequestParam(name = "enabled", required = false) Boolean enabled) {

        log.debug("获取交易对列表：仅启用={}", enabled);

        List<MarketDataDTO.SymbolInfo> symbols;

        if (enabled != null && enabled) {
            symbols = marketDataService.getEnabledSymbols();
        } else {
            symbols = marketDataService.getAllSymbols();
        }

        return ApiResponse.success(symbols);
    }

    /**
     * 获取市场统计数据
     * <p>
     * 获取市场整体统计数据，包括总市值、24小时交易量、活跃交易对数量等。
     * <p>
     * 数据来源：从Redis缓存获取，由Flink作业定期更新。
     * <p>
     * 示例请求：{@code GET /api/market/statistics}
     *
     * @return 市场统计信息，包含总市值、交易量、交易对数量等
     */
    @GetMapping("/statistics")
    public ApiResponse<MarketDataDTO.MarketStatistics> getMarketStatistics() {
        log.debug("获取市场统计数据");

        MarketDataDTO.MarketStatistics statistics = marketDataService.getMarketStatistics();

        return ApiResponse.success(statistics);
    }

    /**
     * 获取24小时行情数据
     * <p>
     * 获取指定交易对过去24小时的行情数据，包括开盘价、收盘价、最高价、最低价、成交量等。
     * <p>
     * 示例请求：{@code GET /api/market/24h/BTCUSDT}
     *
     * @param symbol 交易对符号，路径参数，如"BTCUSDT"
     * @return 24小时行情数据，包含价格、涨跌幅、成交量等信息
     */
    @GetMapping("/24h/{symbol}")
    public ApiResponse<MarketDataDTO> get24hMarketData(@PathVariable String symbol) {
        log.debug("获取24小时行情数据：交易对={}", symbol);

        MarketDataDTO marketData = marketDataService.get24hMarketData(symbol);

        if (marketData == null) {
            return ApiResponse.notFound("Market data not found for symbol: " + symbol);
        }

        return ApiResponse.success(marketData);
    }

    /**
     * 刷新价格缓存
     * <p>
     * 强制刷新指定交易对的价格缓存，从OKX API重新获取最新价格并更新Redis缓存。
     * <p>
     * 示例请求：{@code POST /api/market/refresh/BTCUSDT}
     *
     * @param symbol 交易对符号，路径参数，如"BTCUSDT"
     * @return 操作结果，成功返回成功消息，失败返回错误信息
     */
    @PostMapping("/refresh/{symbol}")
    public ApiResponse<Void> refreshPriceCache(@PathVariable String symbol) {
        log.info("刷新价格缓存：交易对={}", symbol);

        if (symbol == null || symbol.isBlank()) {
            return ApiResponse.badRequest("Symbol is required");
        }

        try {
            marketDataService.refreshPriceCache(symbol);
            return ApiResponse.success("Price cache refreshed successfully", null);
        } catch (Exception e) {
            log.error("刷新价格缓存失败：{}", e.getMessage(), e);
            return ApiResponse.error("Failed to refresh price cache: " + e.getMessage());
        }
    }

    /**
     * 获取市场行情数据列表
     * <p>
     * 获取所有启用的交易对的市场行情数据列表，用于前端Market页面展示。
     * <p>
     * 示例请求：{@code GET /api/market/data}
     *
     * @return 所有启用的交易对的市场行情数据列表，包含价格、涨跌幅、成交量等
     */
    @GetMapping("/data")
    public ApiResponse<List<MarketDataDTO>> getMarketData() {
        log.debug("获取市场行情数据列表");

        try {
            List<MarketDataDTO> marketDataList = marketDataService.getMarketDataList();
            return ApiResponse.success(marketDataList);
        } catch (Exception e) {
            log.error("获取市场行情数据列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("Failed to get market data list: " + e.getMessage());
        }
    }

    /**
     * 获取市场概览统计
     * <p>
     * 获取市场整体概览统计信息，用于前端Dashboard页面展示。
     * <p>
     * 示例请求：{@code GET /api/market/overview}
     *
     * @return 市场概览统计信息，包含总市值、交易量、涨跌趋势等
     */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> getMarketOverview() {
        log.debug("获取市场概览统计");

        try {
            Map<String, Object> overview = marketDataService.getMarketOverview();
            return ApiResponse.success(overview);
        } catch (Exception e) {
            log.error("获取市场概览统计失败：{}", e.getMessage(), e);
            return ApiResponse.error("Failed to get market overview: " + e.getMessage());
        }
    }
}
