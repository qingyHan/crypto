package com.crypto.api.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crypto.api.dto.ApiResponse;
import com.crypto.api.dto.PageResult;
import com.crypto.api.entity.SymbolConfig;
import com.crypto.api.service.SymbolConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 交易对配置管理控制器
 * <p>
 * 提供交易对配置的增删改查功能，是系统交易对管理的主要接口。
 * <p>
 * 主要功能：
 * <ul>
 * <li>交易对查询：查询所有交易对、启用的交易对、可采集的交易对</li>
 * <li>交易对管理：新增、更新、删除交易对配置</li>
 * <li>状态管理：启用/禁用交易对</li>
 * <li>批量操作：批量删除交易对</li>
 * </ul>
 * <p>
 * 数据来源：从MySQL数据库的symbol_config表读取交易对配置数据。
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/symbol-config")
public class SymbolConfigController {

    @Autowired
    private SymbolConfigService symbolConfigService;

    /**
     * 查询所有启用的交易对
     * <p>
     * 获取所有启用状态为true的交易对列表，用于数据采集和前端展示。
     * <p>
     * 示例请求：{@code GET /api/symbol-config/active}
     *
     * @return 启用的交易对列表
     */
    @GetMapping("/active")
    public ApiResponse<List<SymbolConfig>> getActiveSymbols() {
        List<SymbolConfig> symbols = symbolConfigService.getAllActiveSymbols();
        return ApiResponse.success(symbols);
    }

    /**
     * 分页查询交易对配置
     * <p>
     * 支持按交易对符号和启用状态筛选，返回分页结果。
     * <p>
     * 示例请求：{@code GET /api/symbol-config/page?current=1&size=20&symbol=BTC&isActive=true}
     *
     * @param current  当前页码，可选，默认1
     * @param size     每页大小，可选，默认20
     * @param symbol   交易对符号，可选，用于模糊查询
     * @param isActive 是否启用，可选，true表示仅查询启用的，false表示仅查询禁用的
     * @return 交易对配置分页结果
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<SymbolConfig>> querySymbolConfigs(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) Boolean isActive) {

        Page<SymbolConfig> page = symbolConfigService.querySymbolConfigs(current, size, symbol, isActive);

        PageResult<SymbolConfig> result = new PageResult<>();
        result.setRecords(page.getRecords());
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());

        return ApiResponse.success(result);
    }

    /**
     * 根据ID获取交易对配置
     * <p>
     * 根据交易对配置ID获取完整的配置信息。
     * <p>
     * 示例请求：{@code GET /api/symbol-config/123}
     *
     * @param id 交易对配置ID，路径参数
     * @return 交易对配置对象，如果不存在返回错误
     */
    @GetMapping("/{id}")
    public ApiResponse<SymbolConfig> getSymbolConfigById(@PathVariable Long id) {
        SymbolConfig config = symbolConfigService.getById(id);
        if (config == null) {
            return ApiResponse.error("交易对配置不存在");
        }
        return ApiResponse.success(config);
    }

    /**
     * 新增交易对配置
     * <p>
     * 创建新的交易对配置，包括交易对符号、基础货币、计价货币等信息。
     * <p>
     * 示例请求：{@code POST /api/symbol-config}
     * <p>
     * 请求体：交易对配置对象，必须包含symbol、baseCurrency、quoteCurrency
     *
     * @param config 交易对配置对象，请求体
     * @return 操作结果，成功返回成功消息，失败返回错误信息
     */
    @PostMapping
    public ApiResponse<String> addSymbolConfig(@RequestBody SymbolConfig config) {
        // 参数校验：检查必填字段
        if (config.getSymbol() == null || config.getSymbol().isEmpty()) {
            return ApiResponse.error("交易对符号不能为空");
        }
        if (config.getBaseCurrency() == null || config.getBaseCurrency().isEmpty()) {
            return ApiResponse.error("基础货币不能为空");
        }
        if (config.getQuoteCurrency() == null || config.getQuoteCurrency().isEmpty()) {
            return ApiResponse.error("计价货币不能为空");
        }

        boolean success = symbolConfigService.addSymbolConfig(config);
        if (success) {
            log.info("成功添加交易对配置：{}", config.getSymbol());
            return ApiResponse.success("添加成功");
        } else {
            return ApiResponse.error("添加失败，交易对可能已存在");
        }
    }

    /**
     * 更新交易对配置
     * <p>
     * 更新指定交易对配置的信息，包括交易对符号、基础货币、计价货币、启用状态等。
     * <p>
     * 示例请求：{@code PUT /api/symbol-config/123}
     * <p>
     * 请求体：交易对配置对象，包含要更新的字段
     *
     * @param id     交易对配置ID，路径参数
     * @param config 交易对配置对象，请求体，包含要更新的字段
     * @return 操作结果，成功返回成功消息，失败返回错误信息
     */
    @PutMapping("/{id}")
    public ApiResponse<String> updateSymbolConfig(
            @PathVariable Long id,
            @RequestBody SymbolConfig config) {

        config.setId(id);
        boolean success = symbolConfigService.updateSymbolConfig(config);
        if (success) {
            log.info("成功更新交易对配置：ID={}", id);
            return ApiResponse.success("更新成功");
        } else {
            return ApiResponse.error("更新失败");
        }
    }

    /**
     * 删除交易对配置
     */
    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteSymbolConfig(@PathVariable Long id) {
        boolean success = symbolConfigService.deleteSymbolConfig(id);
        if (success) {
            log.info("成功删除交易对配置：ID={}", id);
            return ApiResponse.success("删除成功");
        } else {
            return ApiResponse.error("删除失败");
        }
    }

    /**
     * 启用/禁用交易对
     */
    @PatchMapping("/{id}/toggle")
    public ApiResponse<String> toggleSymbolStatus(
            @PathVariable Long id,
            @RequestParam boolean isActive) {

        boolean success = symbolConfigService.toggleSymbolStatus(id, isActive);
        if (success) {
            log.info("成功切换交易对配置状态：ID={}，状态={}", id, isActive ? "启用" : "禁用");
            return ApiResponse.success(isActive ? "已启用" : "已禁用");
        } else {
            return ApiResponse.error("操作失败");
        }
    }

    /**
     * 批量删除交易对配置
     */
    @DeleteMapping("/batch")
    public ApiResponse<String> batchDelete(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.error("请选择要删除的交易对");
        }

        boolean success = symbolConfigService.batchDelete(ids);
        if (success) {
            log.info("成功批量删除交易对配置：数量={}", ids.size());
            return ApiResponse.success("批量删除成功");
        } else {
            return ApiResponse.error("批量删除失败");
        }
    }

    /**
     * 获取所有可采集的交易对
     */
    @GetMapping("/collectable")
    public ApiResponse<List<SymbolConfig>> getCollectableSymbols() {
        List<SymbolConfig> symbols = symbolConfigService.getCollectableSymbols();
        return ApiResponse.success(symbols);
    }
}
