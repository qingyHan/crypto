package com.crypto.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.crypto.api.dto.AlertQueryDTO;
import com.crypto.api.dto.PageResult;
import com.crypto.api.repository.AlertRecordMapper;
import com.crypto.api.service.AlertService;
import com.crypto.common.entity.AlertRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 预警服务实现
 * <p>
 * 基于 MyBatis Plus 实现预警记录的持久化操作，并处理多维度的查询请求。
 * 也是 Kafka 消费端落地数据的核心组件。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@Service
public class AlertServiceImpl implements AlertService {

    private final AlertRecordMapper alertRecordMapper;

    public AlertServiceImpl(AlertRecordMapper alertRecordMapper) {
        this.alertRecordMapper = alertRecordMapper;
    }

    /**
     * 分页查询预警列表
     * <p>
     * 支持多条件组合查询，包括交易对符号、预警类型、严重程度、时间范围等。
     * 使用MyBatis Plus的LambdaQueryWrapper构建动态查询条件。
     *
     * @param queryDTO 查询条件对象，包含所有查询参数和分页信息
     * @return 分页结果，包含预警列表和分页信息
     * @throws IllegalArgumentException 如果时间范围无效
     */
    @Override
    public PageResult<AlertRecord> queryAlerts(AlertQueryDTO queryDTO) {
        // 初始化默认值（分页参数、排序参数等）
        queryDTO.initDefaults();

        // 验证时间范围：开始时间必须早于结束时间
        if (!queryDTO.isValidTimeRange()) {
            throw new IllegalArgumentException("Invalid time range: startTime must be before endTime");
        }

        // 构建MyBatis Plus查询条件
        LambdaQueryWrapper<AlertRecord> queryWrapper = new LambdaQueryWrapper<>();

        // 交易对符号模糊查询
        if (queryDTO.getSymbol() != null && !queryDTO.getSymbol().isEmpty()) {
            queryWrapper.like(AlertRecord::getSymbol, queryDTO.getSymbol());
        }

        // 预警类型
        if (queryDTO.getAlertType() != null) {
            queryWrapper.eq(AlertRecord::getAlertType, queryDTO.getAlertType());
        }

        // 严重程度
        if (queryDTO.getSeverity() != null) {
            queryWrapper.eq(AlertRecord::getSeverity, queryDTO.getSeverity());
        }

        // 是否已读
        if (queryDTO.getIsRead() != null) {
            queryWrapper.eq(AlertRecord::getIsRead, queryDTO.getIsRead());
        }

        // 时间范围
        if (queryDTO.getStartTime() != null) {
            queryWrapper.ge(AlertRecord::getTriggerTime, queryDTO.getStartTime());
        }
        if (queryDTO.getEndTime() != null) {
            queryWrapper.le(AlertRecord::getTriggerTime, queryDTO.getEndTime());
        }

        // 排序
        String orderBy = queryDTO.getOrderBy();
        boolean isDesc = "desc".equalsIgnoreCase(queryDTO.getOrderDirection());

        switch (orderBy) {
            case "trigger_time":
                queryWrapper.orderBy(true, !isDesc, AlertRecord::getTriggerTime);
                break;
            case "created_at":
                queryWrapper.orderBy(true, !isDesc, AlertRecord::getCreatedAt);
                break;
            case "severity":
                queryWrapper.orderBy(true, !isDesc, AlertRecord::getSeverity);
                break;
            default:
                queryWrapper.orderByDesc(AlertRecord::getTriggerTime);
        }

        // 分页查询
        Page<AlertRecord> page = new Page<>(queryDTO.getCurrent(), queryDTO.getSize());
        Page<AlertRecord> resultPage = alertRecordMapper.selectPage(page, queryWrapper);

        log.debug("查询预警列表：返回{}条，总计{}条", resultPage.getRecords().size(), resultPage.getTotal());

        return PageResult.of(resultPage);
    }

    /**
     * 根据ID获取预警详情
     *
     * @param id 预警ID
     * @return 预警记录对象，如果不存在则返回null
     */
    @Override
    public AlertRecord getAlertById(Long id) {
        AlertRecord alert = alertRecordMapper.selectById(id);
        if (alert == null) {
            log.warn("预警记录不存在：id={}", id);
        }
        return alert;
    }

    /**
     * 标记预警为已读
     * <p>
     * 将指定预警的isRead字段设置为true，并更新updatedAt时间戳。
     *
     * @param id 预警ID
     * @return 操作是否成功
     */
    @Override
    @Transactional
    public boolean markAsRead(Long id) {
        LambdaUpdateWrapper<AlertRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AlertRecord::getId, id)
                .set(AlertRecord::getIsRead, true)
                .set(AlertRecord::getUpdatedAt, LocalDateTime.now());

        int updated = alertRecordMapper.update(null, updateWrapper);
        log.debug("标记预警为已读：id={}，结果={}", id, updated > 0 ? "成功" : "失败");
        return updated > 0;
    }

    /**
     * 批量标记预警为已读
     * <p>
     * 一次性将多个预警标记为已读，提高操作效率。
     *
     * @param ids 预警ID列表
     * @return 成功标记的数量
     */
    @Override
    @Transactional
    public int batchMarkAsRead(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        LambdaUpdateWrapper<AlertRecord> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.in(AlertRecord::getId, ids)
                .set(AlertRecord::getIsRead, true)
                .set(AlertRecord::getUpdatedAt, LocalDateTime.now());

        int count = alertRecordMapper.update(null, updateWrapper);
        log.info("批量标记预警为已读：成功{}条", count);
        return count;
    }

    /**
     * 删除预警
     * <p>
     * 根据预警ID删除预警记录，删除操作不可恢复。
     *
     * @param id 预警ID
     * @return 操作是否成功
     */
    @Override
    @Transactional
    public boolean deleteAlert(Long id) {
        int deleted = alertRecordMapper.deleteById(id);
        log.debug("删除预警：id={}，结果={}", id, deleted > 0 ? "成功" : "失败");
        return deleted > 0;
    }

    /**
     * 批量删除预警
     * <p>
     * 一次性删除多个预警记录，删除操作不可恢复。
     *
     * @param ids 预警ID列表
     * @return 成功删除的数量
     */
    @Override
    @Transactional
    public int batchDeleteAlerts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }

        int count = alertRecordMapper.deleteBatchIds(ids);
        log.info("批量删除预警：成功删除{}条", count);
        return count;
    }

    @Override
    public Map<String, Object> getAlertStatistics(LocalDateTime startTime, LocalDateTime endTime) {
        // Not yet implemented - to be added with custom SQL
        log.debug("预警统计功能尚未实现");
        return Map.of();
    }

    @Override
    public List<Map<String, Object>> countByType(LocalDateTime startTime, LocalDateTime endTime) {
        // Not yet implemented - to be added with custom SQL
        return List.of();
    }

    @Override
    public List<Map<String, Object>> countBySeverity(LocalDateTime startTime, LocalDateTime endTime) {
        // Not yet implemented - to be added with custom SQL
        return List.of();
    }

    /**
     * 统计未读预警数量
     * <p>
     * 统计所有未读预警的总数，用于前端显示未读提示。
     *
     * @return 未读预警数量
     */
    @Override
    public Long countUnread() {
        QueryWrapper<AlertRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("is_read", 0);
        return alertRecordMapper.selectCount(wrapper);
    }

    /**
     * 获取交易对的最近预警
     * <p>
     * 获取指定交易对最近的预警记录，按触发时间倒序排列。
     *
     * @param symbol 交易对符号，如"BTCUSDT"
     * @param limit  限制数量，范围1-100，默认10
     * @return 最近预警列表，按触发时间倒序
     */
    @Override
    public List<AlertRecord> getRecentAlertsBySymbol(String symbol, int limit) {
        // 限制数量范围：1-100，默认10
        if (limit <= 0) {
            limit = 10;
        }
        if (limit > 100) {
            limit = 100;
        }
        QueryWrapper<AlertRecord> wrapper = new QueryWrapper<>();
        wrapper.eq("symbol", symbol).orderByDesc("trigger_time").last("LIMIT " + limit);
        return alertRecordMapper.selectList(wrapper);
    }

    /**
     * 保存预警记录
     * <p>
     * 将预警记录保存到MySQL数据库，自动设置创建时间、更新时间等字段。
     * 此方法通常由KafkaConsumerService调用，用于持久化从Kafka消费的预警数据。
     *
     * @param alertRecord 预警记录对象
     * @return 操作是否成功
     */
    @Override
    @Transactional
    public boolean saveAlert(AlertRecord alertRecord) {
        if (alertRecord == null) {
            return false;
        }

        // 设置创建时间和更新时间（如果未设置）
        if (alertRecord.getCreatedAt() == null) {
            alertRecord.setCreatedAt(LocalDateTime.now());
        }
        if (alertRecord.getUpdatedAt() == null) {
            alertRecord.setUpdatedAt(LocalDateTime.now());
        }

        // 默认未读状态
        if (alertRecord.getIsRead() == null) {
            alertRecord.setIsRead(false);
        }

        int inserted = alertRecordMapper.insert(alertRecord);
        log.debug("保存预警记录：id={}，交易对={}，类型={}",
                alertRecord.getId(), alertRecord.getSymbol(), alertRecord.getAlertType());
        return inserted > 0;
    }

    /**
     * 清理过期预警
     * <p>
     * 删除超过指定天数的预警记录，用于数据清理和维护。
     * 默认保留30天，删除操作不可恢复。
     *
     * @param days 保留天数，默认30天，表示删除30天前的预警
     * @return 删除的预警数量
     */
    @Override
    @Transactional
    public int deleteExpiredAlerts(int days) {
        // 默认保留30天
        if (days <= 0) {
            days = 30;
        }

        // 计算截止时间：当前时间减去保留天数
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(days);

        // 先统计要删除的数量
        LambdaQueryWrapper<AlertRecord> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(AlertRecord::getTriggerTime, cutoffTime);
        long countLong = alertRecordMapper.selectCount(queryWrapper);
        int count = (int) countLong;

        // 执行删除操作
        LambdaUpdateWrapper<AlertRecord> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.lt(AlertRecord::getTriggerTime, cutoffTime);
        alertRecordMapper.delete(deleteWrapper);

        log.info("清理过期预警：删除{}条（保留{}天）", count, days);
        return count;
    }
}
