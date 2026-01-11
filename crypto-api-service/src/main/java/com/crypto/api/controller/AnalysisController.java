package com.crypto.api.controller;

import com.crypto.api.dto.ApiResponse;
import com.crypto.api.service.AnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 数据分析控制器
 * <p>
 * 基于 ClickHouse 的深度数据分析接口，提供趋势识别、技术指标计算及市场情绪分析等功能。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/analysis")
@Slf4j
public class AnalysisController {

    private final AnalysisService analysisService;

    public AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    /**
     * 获取交易对趋势分析
     * <p>
     * 分析指定交易对的价格趋势方向和强度，识别上涨、下跌或横盘趋势。
     * <p>
     * 示例请求：{@code GET /api/analysis/trend/BTCUSDT?period=60}
     *
     * @param symbol 交易对代码，路径参数，如"BTCUSDT"
     * @param period 分析周期（分钟），可选，默认60，表示分析最近60分钟的数据
     * @return 趋势分析结果，包含趋势方向、强度、价格变化等信息
     */
    @GetMapping("/trend/{symbol}")
    public ApiResponse<Map<String, Object>> getTrendAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "60") int period) {

        log.info("获取趋势分析: symbol={}, period={}", symbol, period);

        try {
            Map<String, Object> trend = analysisService.getTrendAnalysis(symbol, period);
            return ApiResponse.success(trend);
        } catch (Exception e) {
            log.error("获取趋势分析失败", e);
            return ApiResponse.error("获取趋势分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取技术指标
     * <p>
     * 计算并返回指定交易对的技术指标，包括RSI、MACD、MA等常用指标。
     * <p>
     * 示例请求：{@code GET /api/analysis/indicators/BTCUSDT}
     *
     * @param symbol 交易对代码，路径参数，如"BTCUSDT"
     * @return 技术指标Map，包含RSI、MACD、MA等指标值
     */
    @GetMapping("/indicators/{symbol}")
    public ApiResponse<Map<String, Object>> getTechnicalIndicators(
            @PathVariable String symbol) {

        log.info("获取技术指标: symbol={}", symbol);

        try {
            Map<String, Object> indicators = analysisService.getTechnicalIndicators(symbol);
            return ApiResponse.success(indicators);
        } catch (Exception e) {
            log.error("获取技术指标失败", e);
            return ApiResponse.error("获取技术指标失败: " + e.getMessage());
        }
    }

    /**
     * 检测价格异常
     * <p>
     * 检测指定交易对在指定时间窗口内的价格异常，识别价格剧烈波动。
     * <p>
     * 示例请求：{@code GET /api/analysis/anomaly/BTCUSDT?window=30}
     *
     * @param symbol 交易对代码，路径参数，如"BTCUSDT"
     * @param window 检测窗口（分钟），可选，默认30，表示检测最近30分钟的数据
     * @return 异常检测结果，包含异常点、异常类型、异常程度等信息
     */
    @GetMapping("/anomaly/{symbol}")
    public ApiResponse<Map<String, Object>> detectAnomalies(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "30") int window) {

        log.info("检测价格异常: symbol={}, window={}", symbol, window);

        try {
            Map<String, Object> anomalies = analysisService.detectAnomalies(symbol, window);
            return ApiResponse.success(anomalies);
        } catch (Exception e) {
            log.error("检测价格异常失败", e);
            return ApiResponse.error("检测价格异常失败: " + e.getMessage());
        }
    }

    /**
     * 获取支撑位和压力位
     * <p>
     * 计算指定交易对的关键支撑位和压力位，用于技术分析。
     * <p>
     * 示例请求：{@code GET /api/analysis/levels/BTCUSDT?hours=24}
     *
     * @param symbol 交易对代码，路径参数，如"BTCUSDT"
     * @param hours  分析窗口（小时），可选，默认24，表示分析最近24小时的数据
     * @return 支撑位和压力位信息，包含关键价格位和强度
     */
    @GetMapping("/levels/{symbol}")
    public ApiResponse<Map<String, Object>> getSupportResistanceLevels(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "24") int hours) {

        log.info("获取支撑/压力位: symbol={}, hours={}", symbol, hours);

        try {
            Map<String, Object> levels = analysisService.getSupportResistanceLevels(symbol, hours);
            return ApiResponse.success(levels);
        } catch (Exception e) {
            log.error("获取支撑/压力位失败", e);
            return ApiResponse.error("获取支撑/压力位失败: " + e.getMessage());
        }
    }

    /**
     * 获取市场情绪分析
     * <p>
     * 基于价格变化和交易量分析市场情绪，判断市场是看涨、看跌还是中性。
     * <p>
     * 示例请求：{@code GET /api/analysis/sentiment/BTCUSDT}
     *
     * @param symbol 交易对代码，路径参数，如"BTCUSDT"
     * @return 市场情绪分析结果，包含情绪类型、情绪强度等信息
     */
    @GetMapping("/sentiment/{symbol}")
    public ApiResponse<Map<String, Object>> getMarketSentiment(
            @PathVariable String symbol) {

        log.info("获取市场情绪: symbol={}", symbol);

        try {
            Map<String, Object> sentiment = analysisService.getMarketSentiment(symbol);
            return ApiResponse.success(sentiment);
        } catch (Exception e) {
            log.error("获取市场情绪失败", e);
            return ApiResponse.error("获取市场情绪失败: " + e.getMessage());
        }
    }

    /**
     * 获取综合分析报告
     * <p>
     * 生成指定交易对的综合分析报告，包含趋势、技术指标、异常检测、支撑压力位、市场情绪等所有分析结果。
     * <p>
     * 示例请求：{@code GET /api/analysis/report/BTCUSDT}
     *
     * @param symbol 交易对代码，路径参数，如"BTCUSDT"
     * @return 综合分析报告，包含所有分析维度的结果
     */
    @GetMapping("/report/{symbol}")
    public ApiResponse<Map<String, Object>> getComprehensiveReport(
            @PathVariable String symbol) {

        log.info("获取综合分析报告: symbol={}", symbol);

        try {
            Map<String, Object> report = analysisService.getComprehensiveReport(symbol);
            return ApiResponse.success(report);
        } catch (Exception e) {
            log.error("获取综合分析报告失败", e);
            return ApiResponse.error("获取综合分析报告失败: " + e.getMessage());
        }
    }

    /**
     * 批量获取市场概览分析
     * <p>
     * 批量分析多个交易对的市场概览，返回每个交易对的关键分析指标。
     * <p>
     * 示例请求：{@code GET /api/analysis/overview?symbols=BTCUSDT,ETHUSDT}
     *
     * @param symbols 交易对列表，可选，逗号分隔，如"BTCUSDT,ETHUSDT"，如果不提供则分析所有交易对
     * @return 市场概览分析列表，每个元素包含一个交易对的分析结果
     */
    @GetMapping("/overview")
    public ApiResponse<List<Map<String, Object>>> getMarketOverview(
            @RequestParam(required = false) String symbols) {

        log.info("获取市场概览分析: symbols={}", symbols);

        try {
            List<Map<String, Object>> overview = analysisService.getMarketOverview(symbols);
            return ApiResponse.success(overview);
        } catch (Exception e) {
            log.error("获取市场概览分析失败", e);
            return ApiResponse.error("获取市场概览分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取波动率分析
     * <p>
     * 计算指定交易对的价格波动率，用于评估市场风险和波动程度。
     * <p>
     * 示例请求：{@code GET /api/analysis/volatility/BTCUSDT?hours=24}
     *
     * @param symbol 交易对代码，路径参数，如"BTCUSDT"
     * @param hours  统计周期（小时），可选，默认24，表示统计最近24小时的数据
     * @return 波动率分析结果，包含波动率值、波动趋势等信息
     */
    @GetMapping("/volatility/{symbol}")
    public ApiResponse<Map<String, Object>> getVolatilityAnalysis(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "24") int hours) {

        log.info("获取波动率分析: symbol={}, hours={}", symbol, hours);

        try {
            Map<String, Object> volatility = analysisService.getVolatilityAnalysis(symbol, hours);
            return ApiResponse.success(volatility);
        } catch (Exception e) {
            log.error("获取波动率分析失败", e);
            return ApiResponse.error("获取波动率分析失败: " + e.getMessage());
        }
    }

    /**
     * 获取相关性分析
     * <p>
     * 分析两个交易对之间的价格相关性，用于投资组合分析和风险控制。
     * <p>
     * 示例请求：{@code GET /api/analysis/correlation?symbol1=BTCUSDT&symbol2=ETHUSDT&hours=24}
     *
     * @param symbol1 交易对1，请求参数，如"BTCUSDT"
     * @param symbol2 交易对2，请求参数，如"ETHUSDT"
     * @param hours   分析周期（小时），可选，默认24，表示分析最近24小时的数据
     * @return 相关性分析结果，包含相关系数、相关性强度等信息
     */
    @GetMapping("/correlation")
    public ApiResponse<Map<String, Object>> getCorrelationAnalysis(
            @RequestParam(required = false) String symbol1,
            @RequestParam(required = false) String symbol2,
            @RequestParam(required = false) String symbols,
            @RequestParam(defaultValue = "24") int hours) {

        try {
            // 如果提供了symbols参数（批量分析）
            if (symbols != null && !symbols.isEmpty()) {
                List<String> symbolList = Arrays.asList(symbols.split(","));
                log.info("获取批量相关性分析: symbols={}, hours={}", symbols, hours);
                Map<String, Object> matrix = analysisService.getCorrelationMatrix(symbolList, hours);
                return ApiResponse.success(matrix);
            }

            // 单个相关性分析
            if (symbol1 == null || symbol2 == null) {
                return ApiResponse.error("请提供symbol1和symbol2参数，或提供symbols参数进行批量分析");
            }

            log.info("获取相关性分析: symbol1={}, symbol2={}, hours={}", symbol1, symbol2, hours);
            Map<String, Object> correlation = analysisService.getCorrelationAnalysis(symbol1, symbol2, hours);
            return ApiResponse.success(correlation);
        } catch (Exception e) {
            log.error("获取相关性分析失败", e);
            return ApiResponse.error("获取相关性分析失败: " + e.getMessage());
        }
    }
}
