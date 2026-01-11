package com.crypto.api.controller;

import com.crypto.api.dto.ApiResponse;
import com.crypto.api.dto.PageResult;
import com.crypto.api.dto.StrategyDTO;
import com.crypto.api.entity.StrategyConfig;
import com.crypto.api.service.StrategyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略管理控制器
 * <p>
 * 负责策略配置的 CRUD 操作及状态管理。策略配置将同步至 Flink 引擎生效。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping(value = "/api/strategies", produces = "application/json;charset=UTF-8")
public class StrategyController {

    private final StrategyService strategyService;

    public StrategyController(StrategyService strategyService) {
        this.strategyService = strategyService;
    }

    /**
     * 查询策略列表
     * <p>
     * 支持分页查询和全量查询两种模式：
     * <ul>
     * <li>提供分页参数：返回分页结果，用于列表页面</li>
     * <li>不提供分页参数：返回所有策略，用于下拉列表等场景</li>
     * </ul>
     * <p>
     * 示例请求：
     * <ul>
     * <li>{@code GET /api/strategies?current=1&size=20} - 分页查询</li>
     * <li>{@code GET /api/strategies} - 返回所有策略</li>
     * </ul>
     *
     * @param current 当前页码，可选，默认1
     * @param size    每页大小，可选，默认20
     * @return 策略列表或分页结果
     */
    @GetMapping
    public ApiResponse<?> queryStrategies(
            @RequestParam(required = false) Long current,
            @RequestParam(required = false) Long size) {

        log.debug("查询策略列表：当前页={}，每页大小={}", current, size);

        // 如果没有分页参数，返回所有策略（兼容前端下拉列表等场景）
        if (current == null && size == null) {
            List<StrategyConfig> allStrategies = strategyService.queryStrategies(1L, 1000L).getRecords();
            return ApiResponse.success(allStrategies);
        }

        // 返回分页结果
        PageResult<StrategyConfig> result = strategyService.queryStrategies(
                current != null ? current : 1L,
                size != null ? size : 20L);

        return ApiResponse.success(result);
    }

    /**
     * 根据ID获取策略详情
     * <p>
     * 根据策略ID获取完整的策略配置信息。
     * <p>
     * 示例请求：{@code GET /api/strategies/123}
     *
     * @param id 策略ID，路径参数
     * @return 策略详情对象，如果不存在返回404
     */
    @GetMapping("/{id}")
    public ApiResponse<StrategyConfig> getStrategyById(@PathVariable Long id) {
        log.debug("根据ID获取策略详情：id={}", id);

        StrategyConfig strategy = strategyService.getStrategyById(id);

        if (strategy == null) {
            return ApiResponse.notFound("Strategy not found: id=" + id);
        }

        return ApiResponse.success(strategy);
    }

    /**
     * 根据类型查询策略
     * <p>
     * 查询指定类型的所有策略，如PRICE_SPIKE、PRICE_DROP等。
     * <p>
     * 示例请求：{@code GET /api/strategies/type/PRICE_SPIKE}
     *
     * @param strategyType 策略类型，路径参数，如"PRICE_SPIKE"、"PRICE_DROP"
     * @return 策略列表，包含该类型的所有策略
     */
    @GetMapping("/type/{strategyType}")
    public ApiResponse<List<StrategyConfig>> getStrategiesByType(@PathVariable String strategyType) {
        log.debug("根据类型查询策略：类型={}", strategyType);

        List<StrategyConfig> strategies = strategyService.getStrategiesByType(strategyType);

        return ApiResponse.success(strategies);
    }

    /**
     * 查询所有启用的策略
     * <p>
     * 获取所有启用状态为true的策略列表，用于Flink作业读取策略配置。
     * <p>
     * 示例请求：{@code GET /api/strategies/enabled}
     *
     * @return 启用的策略列表，包含所有启用状态的策略配置
     */
    @GetMapping("/enabled")
    public ApiResponse<List<StrategyConfig>> getAllEnabledStrategies() {
        log.debug("获取所有启用的策略");

        List<StrategyConfig> strategies = strategyService.getAllEnabledStrategies();

        return ApiResponse.success(strategies);
    }

    /**
     * 创建策略
     * <p>
     * 创建新的策略配置，策略创建后默认状态为启用。
     * <p>
     * 示例请求：{@code POST /api/strategies}
     * <p>
     * 请求体：策略DTO对象，包含策略名称、类型、参数等
     *
     * @param strategyDTO 策略DTO对象，包含策略配置信息
     * @return 创建的策略对象
     */
    @PostMapping
    public ApiResponse<StrategyConfig> createStrategy(@RequestBody StrategyDTO strategyDTO) {
        log.info("创建策略：名称={}，类型={}", strategyDTO.getStrategyName(), strategyDTO.getStrategyType());

        try {
            StrategyConfig strategy = strategyService.createStrategy(strategyDTO);
            return ApiResponse.success("Strategy created successfully", strategy);
        } catch (Exception e) {
            log.error("创建策略失败：{}", e.getMessage(), e);
            return ApiResponse.error("Failed to create strategy: " + e.getMessage());
        }
    }

    /**
     * 更新策略
     * <p>
     * 更新指定策略的配置信息，包括策略名称、参数、启用状态等。
     * <p>
     * 示例请求：{@code PUT /api/strategies/123}
     * <p>
     * 请求体：策略DTO对象，包含要更新的配置信息
     *
     * @param id          策略ID，路径参数
     * @param strategyDTO 策略DTO对象，包含要更新的配置信息
     * @return 更新后的策略对象
     */
    @PutMapping("/{id}")
    public ApiResponse<StrategyConfig> updateStrategy(
            @PathVariable Long id,
            @RequestBody StrategyDTO strategyDTO) {

        log.info("更新策略：id={}，名称={}", id, strategyDTO.getStrategyName());

        try {
            StrategyConfig strategy = strategyService.updateStrategy(id, strategyDTO);
            return ApiResponse.success("Strategy updated successfully", strategy);
        } catch (Exception e) {
            log.error("更新策略失败：{}", e.getMessage(), e);
            return ApiResponse.error("Failed to update strategy: " + e.getMessage());
        }
    }

    /**
     * 删除策略
     * <p>
     * 根据策略ID删除策略配置，删除操作不可恢复。
     * <p>
     * 示例请求：{@code DELETE /api/strategies/123}
     *
     * @param id 策略ID，路径参数
     * @return 操作结果，成功返回成功消息，失败返回错误信息
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteStrategy(@PathVariable Long id) {
        log.info("删除策略：id={}", id);

        if (id == null || id <= 0) {
            return ApiResponse.badRequest("Invalid strategy id");
        }

        boolean success = strategyService.deleteStrategy(id);

        if (success) {
            return ApiResponse.success("Strategy deleted successfully", null);
        } else {
            return ApiResponse.error("Failed to delete strategy");
        }
    }

    /**
     * 启用/禁用策略
     * <p>
     * 切换策略的启用状态，禁用的策略不会被Flink作业读取和执行。
     * <p>
     * 示例请求：{@code PUT /api/strategies/123/toggle?enabled=false}
     *
     * @param id      策略ID，路径参数
     * @param enabled 启用状态，请求参数，true表示启用，false表示禁用
     * @return 操作结果，成功返回成功消息，失败返回错误信息
     */
    @PutMapping("/{id}/toggle")
    public ApiResponse<Void> toggleStrategyEnabled(
            @PathVariable Long id,
            @RequestParam Boolean enabled) {

        log.info("切换策略启用状态：id={}，启用={}", id, enabled);

        if (id == null || id <= 0 || enabled == null) {
            return ApiResponse.badRequest("Invalid parameters");
        }

        boolean success = strategyService.toggleStrategyEnabled(id, enabled);

        if (success) {
            String action = enabled ? "enabled" : "disabled";
            return ApiResponse.success("Strategy " + action + " successfully", null);
        } else {
            return ApiResponse.error("Failed to toggle strategy");
        }
    }

    /**
     * 根据交易对查询相关策略
     * <p>
     * 查询指定交易对相关的所有策略配置。
     * <p>
     * 示例请求：{@code GET /api/strategies/symbol/BTCUSDT}
     *
     * @param symbol 交易对符号，路径参数，如"BTCUSDT"
     * @return 策略列表，包含该交易对相关的所有策略
     */
    @GetMapping("/symbol/{symbol}")
    public ApiResponse<List<StrategyConfig>> getStrategiesBySymbol(@PathVariable String symbol) {
        log.debug("根据交易对查询策略：交易对={}", symbol);

        List<StrategyConfig> strategies = strategyService.getStrategiesBySymbol(symbol);

        return ApiResponse.success(strategies);
    }

    /**
     * 获取策略统计信息
     * <p>
     * 统计策略配置的整体情况，包括按类型统计、启用数量、总数量等。
     * <p>
     * 示例请求：{@code GET /api/strategies/statistics}
     *
     * @return 统计信息Map，包含按类型统计、启用数量、总数量等
     */
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics() {
        log.debug("获取策略统计信息");

        Map<String, Object> statistics = new HashMap<>();

        // 按策略类型统计（PRICE_SPIKE、PRICE_DROP等）
        List<Map<String, Object>> typeStats = strategyService.countByType();
        statistics.put("byType", typeStats);

        // 启用的策略数量
        Long enabledCount = strategyService.countEnabled();
        statistics.put("enabledCount", enabledCount);

        // 总策略数量
        PageResult<StrategyConfig> allStrategies = strategyService.queryStrategies(1L, 1L);
        statistics.put("totalCount", allStrategies.getTotal());

        return ApiResponse.success(statistics);
    }

    /**
     * 验证策略配置
     * <p>
     * 验证策略配置的有效性，包括参数格式、数值范围、必填字段等。
     * <p>
     * 示例请求：{@code POST /api/strategies/validate}
     * <p>
     * 请求体：策略DTO对象，包含要验证的策略配置
     *
     * @param strategyDTO 策略DTO对象，包含要验证的策略配置
     * @return 验证结果，包含验证是否通过的信息
     */
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validateStrategy(@RequestBody StrategyDTO strategyDTO) {
        log.debug("验证策略配置：名称={}", strategyDTO.getStrategyName());

        boolean valid = strategyService.validateStrategy(strategyDTO);

        Map<String, Object> result = new HashMap<>();
        result.put("valid", valid);

        if (valid) {
            return ApiResponse.success("Strategy configuration is valid", result);
        } else {
            return ApiResponse.badRequest("Strategy configuration is invalid");
        }
    }
}
