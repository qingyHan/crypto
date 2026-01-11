package com.crypto.api.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crypto.api.entity.StrategyConfig;
import org.springframework.stereotype.Repository;

/**
 * 策略配置Mapper
 * <p>
 * 基于MyBatis Plus的数据访问接口
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Repository
public interface StrategyConfigMapper extends BaseMapper<StrategyConfig> {
}
