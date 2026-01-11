package com.crypto.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crypto.api.dto.PageResult;
import com.crypto.api.dto.StrategyDTO;
import com.crypto.api.entity.StrategyConfig;
import com.crypto.api.repository.StrategyConfigMapper;
import com.crypto.api.service.StrategyService;
import com.crypto.common.exception.DataValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 策略服务实现
 * <p>
 * 管理用户的预警策略配置。包含参数校验、类型转换及 CRUD 操作。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class StrategyServiceImpl implements StrategyService {

    private final StrategyConfigMapper strategyConfigMapper;

    public StrategyServiceImpl(StrategyConfigMapper strategyConfigMapper) {
        this.strategyConfigMapper = strategyConfigMapper;
    }

    @Override
    public PageResult<StrategyConfig> queryStrategies(Long current, Long size) {
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L;
        }

        Page<StrategyConfig> page = new Page<>(current, size);
        LambdaQueryWrapper<StrategyConfig> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(StrategyConfig::getCreatedAt);

        Page<StrategyConfig> resultPage = strategyConfigMapper.selectPage(page, queryWrapper);
        log.debug("Queried {} strategies, total: {}", resultPage.getRecords().size(), resultPage.getTotal());

        return PageResult.of(resultPage);
    }

    @Override
    public List<StrategyConfig> getStrategiesByType(String strategyType) {
        QueryWrapper<StrategyConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1).eq("strategy_type", strategyType);
        return strategyConfigMapper.selectList(wrapper);
    }

    @Override
    public List<StrategyConfig> getAllEnabledStrategies() {
        QueryWrapper<StrategyConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        return strategyConfigMapper.selectList(wrapper);
    }

    @Override
    public StrategyConfig getStrategyById(Long id) {
        StrategyConfig strategy = strategyConfigMapper.selectById(id);
        if (strategy == null) {
            log.warn("Strategy not found: id={}", id);
        }
        return strategy;
    }

    @Override
    @Transactional
    public StrategyConfig createStrategy(StrategyDTO strategyDTO) {
        // 验证策略配置
        if (!validateStrategy(strategyDTO)) {
            throw new DataValidationException("Invalid strategy configuration");
        }

        // 兼容处理：前端传参可能是 Boolean, Integer(0/1) 或 String
        Object enabledObj = strategyDTO.getEnabled();
        Boolean enabled = false;
        if (enabledObj == null) {
            enabled = false;
        } else if (enabledObj instanceof Boolean) {
            enabled = (Boolean) enabledObj;
        } else if (enabledObj instanceof Integer) {
            enabled = ((Integer) enabledObj) == 1;
        } else if (enabledObj instanceof Number) {
            enabled = ((Number) enabledObj).intValue() == 1;
        } else {
            enabled = Boolean.parseBoolean(enabledObj.toString());
        }
        
        // 转换为实体
        StrategyConfig strategy = StrategyConfig.builder()
                .strategyName(strategyDTO.getStrategyName())
                .strategyType(strategyDTO.getStrategyType())
                .symbols(strategyDTO.getSymbols())
                .enabled(enabled)
                .parameters(strategyDTO.getParameters())
                .description(strategyDTO.getDescription())
                .triggerCount(0L)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        int inserted = strategyConfigMapper.insert(strategy);
        if (inserted > 0) {
            log.info("Created strategy: id={}, name={}, type={}",
                    strategy.getId(), strategy.getStrategyName(), strategy.getStrategyType());
            return strategy;
        }

        throw new RuntimeException("Failed to create strategy");
    }

    @Override
    @Transactional
    public StrategyConfig updateStrategy(Long id, StrategyDTO strategyDTO) {
        // 检查策略是否存在
        StrategyConfig existing = strategyConfigMapper.selectById(id);
        if (existing == null) {
            throw new DataValidationException("Strategy not found: id=" + id);
        }

        // 验证策略配置
        if (!validateStrategy(strategyDTO)) {
            throw new DataValidationException("Invalid strategy configuration");
        }

        // 更新字段
        existing.setStrategyName(strategyDTO.getStrategyName());
        existing.setStrategyType(strategyDTO.getStrategyType());
        existing.setSymbols(strategyDTO.getSymbols());
        existing.setParameters(strategyDTO.getParameters());
        existing.setDescription(strategyDTO.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());

        // 转换enabled字段：前端可能传1/0，后端需要Boolean
        if (strategyDTO.getEnabled() != null) {
            Object enabledObj = strategyDTO.getEnabled();
            Boolean enabled = false;
            if (enabledObj instanceof Boolean) {
                enabled = (Boolean) enabledObj;
            } else if (enabledObj instanceof Integer) {
                enabled = ((Integer) enabledObj) == 1;
            } else if (enabledObj instanceof Number) {
                enabled = ((Number) enabledObj).intValue() == 1;
            } else {
                enabled = Boolean.parseBoolean(enabledObj.toString());
            }
            existing.setEnabled(enabled);
        }

        int updated = strategyConfigMapper.updateById(existing);
        if (updated > 0) {
            log.info("Updated strategy: id={}, name={}", id, existing.getStrategyName());
            return existing;
        }

        throw new RuntimeException("Failed to update strategy");
    }

    @Override
    @Transactional
    public boolean deleteStrategy(Long id) {
        int deleted = strategyConfigMapper.deleteById(id);
        log.info("Deleted strategy: id={}, success={}", id, deleted > 0);
        return deleted > 0;
    }

    @Override
    @Transactional
    public boolean toggleStrategyEnabled(Long id, Boolean enabled) {
        StrategyConfig config = new StrategyConfig();
        config.setId(id);
        config.setEnabled(enabled);
        config.setUpdatedAt(LocalDateTime.now());
        int updated = strategyConfigMapper.updateById(config);
        log.info("Toggled strategy {} enabled status to {}: success={}",
                id, enabled, updated > 0);
        return updated > 0;
    }

    @Override
    @Transactional
    public boolean updateTriggerInfo(Long id) {
        StrategyConfig config = new StrategyConfig();
        config.setId(id);
        config.setTriggerCount((long) (strategyConfigMapper.selectById(id) == null ? 0 : strategyConfigMapper.selectById(id).getTriggerCount() + 1));
        config.setLastTriggerTime(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        int updated = strategyConfigMapper.updateById(config);
        log.debug("Updated trigger info for strategy {}: success={}", id, updated > 0);
        return updated > 0;
    }

    @Override
    public List<StrategyConfig> getStrategiesBySymbol(String symbol) {
        QueryWrapper<StrategyConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1).and(qw -> qw.eq("symbols", "*").or().like("symbols", symbol));
        return strategyConfigMapper.selectList(wrapper);
    }

    @Override
    public List<Map<String, Object>> countByType() {
        // Return empty list - custom SQL not available in BaseMapper
        return List.of();
    }

    @Override
    public Long countEnabled() {
        QueryWrapper<StrategyConfig> wrapper = new QueryWrapper<>();
        wrapper.eq("enabled", 1);
        return strategyConfigMapper.selectCount(wrapper);
    }

    @Override
    public boolean validateStrategy(StrategyDTO strategyDTO) {
        if (strategyDTO == null) {
            log.warn("Strategy DTO is null");
            return false;
        }

        if (strategyDTO.getStrategyName() == null || strategyDTO.getStrategyName().trim().isEmpty()) {
            log.warn("Strategy name is required");
            return false;
        }

        if (strategyDTO.getStrategyType() == null || strategyDTO.getStrategyType().trim().isEmpty()) {
            log.warn("Strategy type is required");
            return false;
        }

        // 验证策略类型 - 支持前端使用的类型
        String type = strategyDTO.getStrategyType();
        // 支持的类型：前端类型和后端类型
        boolean isValidType = type.equals("PRICE_SPIKE") || type.equals("PRICE_DROP") 
                || type.equals("PRICE_ALERT") || type.equals("VOLUME_ANOMALY")
                || type.equals("VOLUME_ALERT") || type.equals("WHALE_TRADE")
                || type.equals("WHALE_ALERT") || type.equals("VOLATILITY_ALERT");
        
        if (!isValidType) {
            log.warn("Invalid strategy type: {}", type);
            return false;
        }

        if (strategyDTO.getSymbols() == null || strategyDTO.getSymbols().trim().isEmpty()) {
            log.warn("Symbols are required");
            return false;
        }

        if (strategyDTO.getParameters() == null || strategyDTO.getParameters().isEmpty()) {
            log.warn("Parameters are required");
            return false;
        }

        return true;
    }
}
