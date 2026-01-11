package com.crypto.api.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.crypto.common.entity.AlertRecord;
import org.springframework.stereotype.Repository;

/**
 * 预警记录Mapper
 * <p>
 * 基于MyBatis Plus的数据访问接口
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Repository
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {
}
