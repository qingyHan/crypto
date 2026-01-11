package com.crypto.api.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crypto.api.entity.SymbolConfig;
import com.crypto.api.repository.SymbolConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交易对配置服务
 * 提供交易对的CRUD操作
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Service
@Slf4j
public class SymbolConfigService {

    @Autowired
    private SymbolConfigMapper symbolConfigMapper;

    /**
     * 查询所有启用的交易对
     */
    public List<SymbolConfig> getAllActiveSymbols() {
        LambdaQueryWrapper<SymbolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SymbolConfig::getIsActive, true)
                .orderByAsc(SymbolConfig::getSymbol);
        return symbolConfigMapper.selectList(wrapper);
    }

    /**
     * 分页查询交易对
     */
    public Page<SymbolConfig> querySymbolConfigs(Long current, Long size, String symbol, Boolean isActive) {
        Page<SymbolConfig> page = new Page<>(current, size);
        LambdaQueryWrapper<SymbolConfig> wrapper = new LambdaQueryWrapper<>();

        if (symbol != null && !symbol.isEmpty()) {
            wrapper.like(SymbolConfig::getSymbol, symbol);
        }
        if (isActive != null) {
            wrapper.eq(SymbolConfig::getIsActive, isActive);
        }
        wrapper.orderByDesc(SymbolConfig::getCreatedAt);

        return symbolConfigMapper.selectPage(page, wrapper);
    }

    /**
     * 根据ID获取交易对配置
     */
    public SymbolConfig getById(Long id) {
        return symbolConfigMapper.selectById(id);
    }

    /**
     * 根据symbol获取交易对配置
     */
    public SymbolConfig getBySymbol(String symbol) {
        LambdaQueryWrapper<SymbolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SymbolConfig::getSymbol, symbol);
        return symbolConfigMapper.selectOne(wrapper);
    }

    /**
     * 新增交易对配置
     */
    public boolean addSymbolConfig(SymbolConfig config) {
        // 检查是否已存在
        SymbolConfig existing = getBySymbol(config.getSymbol());
        if (existing != null) {
            log.warn("Symbol {} already exists", config.getSymbol());
            return false;
        }

        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());

        // 默认值设置
        if (config.getIsActive() == null) {
            config.setIsActive(true);
        }
        if (config.getCollectEnabled() == null) {
            config.setCollectEnabled(true);
        }
        if (config.getPricePrecision() == null) {
            config.setPricePrecision(2);
        }

        int result = symbolConfigMapper.insert(config);
        log.info("Added symbol config: {}, result: {}", config.getSymbol(), result);
        return result > 0;
    }

    /**
     * 更新交易对配置
     */
    public boolean updateSymbolConfig(SymbolConfig config) {
        if (config.getId() == null) {
            log.warn("Symbol config ID is null");
            return false;
        }

        config.setUpdatedAt(LocalDateTime.now());
        int result = symbolConfigMapper.updateById(config);
        log.info("Updated symbol config: {}, result: {}", config.getSymbol(), result);
        return result > 0;
    }

    /**
     * 删除交易对配置
     */
    public boolean deleteSymbolConfig(Long id) {
        int result = symbolConfigMapper.deleteById(id);
        log.info("Deleted symbol config ID: {}, result: {}", id, result);
        return result > 0;
    }

    /**
     * 启用/禁用交易对
     */
    public boolean toggleSymbolStatus(Long id, boolean isActive) {
        SymbolConfig config = symbolConfigMapper.selectById(id);
        if (config == null) {
            log.warn("Symbol config not found: {}", id);
            return false;
        }

        config.setIsActive(isActive);
        config.setUpdatedAt(LocalDateTime.now());
        int result = symbolConfigMapper.updateById(config);
        log.info("Toggled symbol {} status to {}", config.getSymbol(), isActive);
        return result > 0;
    }

    /**
     * 批量删除交易对
     */
    public boolean batchDelete(List<Long> ids) {
        int result = symbolConfigMapper.deleteBatchIds(ids);
        log.info("Batch deleted {} symbol configs", result);
        return result > 0;
    }

    /**
     * 获取所有可采集的交易对列表
     */
    public List<SymbolConfig> getCollectableSymbols() {
        LambdaQueryWrapper<SymbolConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SymbolConfig::getIsActive, true)
                .eq(SymbolConfig::getCollectEnabled, true)
                .orderByAsc(SymbolConfig::getSymbol);
        return symbolConfigMapper.selectList(wrapper);
    }
}
