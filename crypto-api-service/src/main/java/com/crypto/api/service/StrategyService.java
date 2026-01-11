package com.crypto.api.service;

import com.crypto.api.dto.PageResult;
import com.crypto.api.dto.StrategyDTO;
import com.crypto.api.entity.StrategyConfig;

import java.util.List;
import java.util.Map;

/**
 * 策略服务接口
 * 提供策略配置的管理功能
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public interface StrategyService {

    /**
     * 分页查询策略列表
     *
     * @param current 当前页码
     * @param size    每页大小
     * @return 分页结果
     */
    PageResult<StrategyConfig> queryStrategies(Long current, Long size);

    /**
     * 根据类型查询策略
     *
     * @param strategyType 策略类型
     * @return 策略列表
     */
    List<StrategyConfig> getStrategiesByType(String strategyType);

    /**
     * 查询所有启用的策略
     *
     * @return 策略列表
     */
    List<StrategyConfig> getAllEnabledStrategies();

    /**
     * 根据ID查询策略
     *
     * @param id 策略ID
     * @return 策略配置
     */
    StrategyConfig getStrategyById(Long id);

    /**
     * 创建策略
     *
     * @param strategyDTO 策略DTO
     * @return 创建的策略
     */
    StrategyConfig createStrategy(StrategyDTO strategyDTO);

    /**
     * 更新策略
     *
     * @param id          策略ID
     * @param strategyDTO 策略DTO
     * @return 更新的策略
     */
    StrategyConfig updateStrategy(Long id, StrategyDTO strategyDTO);

    /**
     * 删除策略
     *
     * @param id 策略ID
     * @return 是否成功
     */
    boolean deleteStrategy(Long id);

    /**
     * 切换策略启用状态
     *
     * @param id      策略ID
     * @param enabled 启用状态
     * @return 是否成功
     */
    boolean toggleStrategyEnabled(Long id, Boolean enabled);

    /**
     * 更新策略触发信息
     *
     * @param id 策略ID
     * @return 是否成功
     */
    boolean updateTriggerInfo(Long id);

    /**
     * 根据交易对查询相关策略
     *
     * @param symbol 交易对符号
     * @return 策略列表
     */
    List<StrategyConfig> getStrategiesBySymbol(String symbol);

    /**
     * 统计各类型策略数量
     *
     * @return 统计结果
     */
    List<Map<String, Object>> countByType();

    /**
     * 统计启用的策略数量
     *
     * @return 启用数量
     */
    Long countEnabled();

    /**
     * 验证策略配置
     *
     * @param strategyDTO 策略DTO
     * @return 验证结果
     */
    boolean validateStrategy(StrategyDTO strategyDTO);
}
