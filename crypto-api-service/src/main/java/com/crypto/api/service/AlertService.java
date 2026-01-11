package com.crypto.api.service;

import com.crypto.api.dto.AlertQueryDTO;
import com.crypto.api.dto.PageResult;
import com.crypto.common.entity.AlertRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 预警服务接口
 * 提供预警记录的查询、管理和统计功能
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public interface AlertService {

    /**
     * 分页查询预警列表
     *
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    PageResult<AlertRecord> queryAlerts(AlertQueryDTO queryDTO);

    /**
     * 根据ID查询预警详情
     *
     * @param id 预警ID
     * @return 预警记录
     */
    AlertRecord getAlertById(Long id);

    /**
     * 标记预警为已读
     *
     * @param id 预警ID
     * @return 是否成功
     */
    boolean markAsRead(Long id);

    /**
     * 批量标记为已读
     *
     * @param ids 预警ID列表
     * @return 成功数量
     */
    int batchMarkAsRead(List<Long> ids);

    /**
     * 删除预警
     *
     * @param id 预警ID
     * @return 是否成功
     */
    boolean deleteAlert(Long id);

    /**
     * 批量删除预警
     *
     * @param ids 预警ID列表
     * @return 成功数量
     */
    int batchDeleteAlerts(List<Long> ids);

    /**
     * 获取预警统计信息
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计信息
     */
    Map<String, Object> getAlertStatistics(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按类型统计预警数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计列表
     */
    List<Map<String, Object>> countByType(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 按严重程度统计预警数量
     *
     * @param startTime 开始时间
     * @param endTime   结束时间
     * @return 统计列表
     */
    List<Map<String, Object>> countBySeverity(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 查询未读预警数量
     *
     * @return 未读数量
     */
    Long countUnread();

    /**
     * 查询最近的预警(按交易对)
     *
     * @param symbol 交易对符号
     * @param limit  限制数量
     * @return 预警列表
     */
    List<AlertRecord> getRecentAlertsBySymbol(String symbol, int limit);

    /**
     * 保存预警记录
     *
     * @param alertRecord 预警记录
     * @return 是否成功
     */
    boolean saveAlert(AlertRecord alertRecord);

    /**
     * 删除过期预警
     *
     * @param days 保留天数
     * @return 删除数量
     */
    int deleteExpiredAlerts(int days);
}
