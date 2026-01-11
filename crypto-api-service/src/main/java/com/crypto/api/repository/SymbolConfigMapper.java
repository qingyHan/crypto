package com.crypto.api.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crypto.api.entity.SymbolConfig;
import org.springframework.stereotype.Repository;

/**
 * 交易对配置Mapper
 * 基于MyBatis Plus的数据访问接口
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Repository
public interface SymbolConfigMapper extends BaseMapper<SymbolConfig> {
}
