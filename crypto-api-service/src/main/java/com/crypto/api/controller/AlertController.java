package com.crypto.api.controller;

import com.crypto.api.dto.AlertQueryDTO;
import com.crypto.api.dto.ApiResponse;
import com.crypto.api.dto.PageResult;
import com.crypto.api.service.AlertService;
import com.crypto.common.entity.AlertRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预警管理控制器
 * <p>
 * 提供预警记录的增删改查及统计接口。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /**
     * 查询预警列表
     * <p>
     * 支持多维度组合查询（交易对、类型、严重程度、时间范围）。
     * </p>
     * 
     * @param queryDTO 查询参数封装
     * @return 分页结果
     */
    @GetMapping
    public ApiResponse<PageResult<AlertRecord>> queryAlerts(AlertQueryDTO queryDTO) {
        log.debug("查询预警列表，条件：{}", queryDTO);

        try {
            PageResult<AlertRecord> result = alertService.queryAlerts(queryDTO);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("查询预警列表失败：{}", e.getMessage(), e);
            return ApiResponse.error("Failed to query alerts: " + e.getMessage());
        }
    }

    /**
     * 获取预警详情
     * <p>
     * 根据预警ID获取完整的预警信息，包括预警内容、触发时间、价格变化等。
     * <p>
     * 示例请求：{@code GET /api/alerts/123}
     *
     * @param id 预警ID，路径参数
     * @return 预警详情对象，如果不存在返回404
     */
    @GetMapping("/{id}")
    public ApiResponse<AlertRecord> getAlertById(@PathVariable Long id) {
        log.debug("根据ID获取预警详情：id={}", id);

        AlertRecord alert = alertService.getAlertById(id);

        if (alert == null) {
            return ApiResponse.notFound("Alert not found: id=" + id);
        }

        return ApiResponse.success(alert);
    }

    /**
     * 标记预警为已读
     * <p>
     * 将指定预警的isRead字段设置为true，并更新updatedAt时间戳。
     * <p>
     * 示例请求：{@code PUT /api/alerts/123/read}
     *
     * @param id 预警ID，路径参数
     * @return 操作结果，成功返回成功消息，失败返回错误信息
     */
    @PutMapping("/{id}/read")
    public ApiResponse<Void> markAsRead(@PathVariable Long id) {
        log.info("标记预警为已读：id={}", id);

        if (id == null || id <= 0) {
            return ApiResponse.badRequest("Invalid alert id");
        }

        boolean success = alertService.markAsRead(id);

        if (success) {
            return ApiResponse.success("Alert marked as read successfully", null);
        } else {
            return ApiResponse.error("Failed to mark alert as read");
        }
    }

    /**
     * 批量标记预警为已读
     * <p>
     * 一次性将多个预警标记为已读，提高操作效率。
     * <p>
     * 示例请求：{@code PUT /api/alerts/batch-read}
     * <p>
     * 请求体：{@code [1, 2, 3, 4, 5]}
     *
     * @param ids 预警ID列表，请求体中的JSON数组
     * @return 操作结果，包含总数和成功数量
     */
    @PutMapping("/batch-read")
    public ApiResponse<Map<String, Object>> batchMarkAsRead(@RequestBody List<Long> ids) {
        log.info("批量标记预警为已读：数量={}", ids.size());

        int count = alertService.batchMarkAsRead(ids);

        Map<String, Object> result = new HashMap<>();
        result.put("total", ids.size());
        result.put("success", count);

        return ApiResponse.success(result);
    }

    /**
     * 删除预警
     * <p>
     * 根据预警ID删除预警记录，删除操作不可恢复。
     * <p>
     * 示例请求：{@code DELETE /api/alerts/123}
     *
     * @param id 预警ID，路径参数
     * @return 操作结果，成功返回成功消息，失败返回错误信息
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAlert(@PathVariable Long id) {
        log.info("删除预警：id={}", id);

        if (id == null || id <= 0) {
            return ApiResponse.badRequest("Invalid alert id");
        }

        boolean success = alertService.deleteAlert(id);

        if (success) {
            return ApiResponse.success("Alert deleted successfully", null);
        } else {
            return ApiResponse.error("Failed to delete alert");
        }
    }

    /**
     * 批量删除预警
     * <p>
     * 一次性删除多个预警记录，删除操作不可恢复。
     * <p>
     * 示例请求：{@code DELETE /api/alerts/batch}
     * <p>
     * 请求体：{@code [1, 2, 3, 4, 5]}
     *
     * @param ids 预警ID列表，请求体中的JSON数组
     * @return 操作结果，包含总数和成功删除数量
     */
    @DeleteMapping("/batch")
    public ApiResponse<Map<String, Object>> batchDeleteAlerts(@RequestBody List<Long> ids) {
        log.info("批量删除预警：数量={}", ids.size());

        int count = alertService.batchDeleteAlerts(ids);

        Map<String, Object> result = new HashMap<>();
        result.put("total", ids.size());
        result.put("success", count);

        return ApiResponse.success(result);
    }

    /**
     * 获取预警统计信息
     * <p>
     * 统计指定时间范围内的预警数据，包括按类型统计、按严重程度统计、未读数量等。
     * <p>
     * 如果未提供时间参数，默认统计最近24小时的数据。
     * <p>
     * 示例请求：{@code GET /api/alerts/statistics?startTime=2025-12-01 00:00:00&endTime=2025-12-01 23:59:59}
     *
     * @param startTime 开始时间，可选，格式：yyyy-MM-dd HH:mm:ss
     * @param endTime   结束时间，可选，格式：yyyy-MM-dd HH:mm:ss
     * @return 统计信息Map，包含按类型统计、按严重程度统计、未读数量等
     */
    @GetMapping("/statistics")
    public ApiResponse<Map<String, Object>> getStatistics(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {

        log.debug("获取预警统计：开始时间={}，结束时间={}", startTime, endTime);

        // 默认查询最近24小时的数据
        if (startTime == null) {
            startTime = LocalDateTime.now().minusHours(24);
        }
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }

        Map<String, Object> statistics = new HashMap<>();

        // 基本统计信息
        Map<String, Object> basicStats = alertService.getAlertStatistics(startTime, endTime);
        statistics.putAll(basicStats);

        // 按预警类型统计（PRICE_SPIKE、PRICE_DROP等）
        List<Map<String, Object>> typeStats = alertService.countByType(startTime, endTime);
        statistics.put("byType", typeStats);

        // 按严重程度统计（LOW、MEDIUM、HIGH、CRITICAL）
        List<Map<String, Object>> severityStats = alertService.countBySeverity(startTime, endTime);
        statistics.put("bySeverity", severityStats);

        // 未读预警数量
        Long unreadCount = alertService.countUnread();
        statistics.put("unreadCount", unreadCount);

        return ApiResponse.success(statistics);
    }

    /**
     * 获取交易对的最近预警
     * <p>
     * 获取指定交易对最近的预警记录，按触发时间倒序排列。
     * <p>
     * 示例请求：{@code GET /api/alerts/recent/BTCUSDT?limit=10}
     *
     * @param symbol 交易对符号，路径参数，如"BTCUSDT"
     * @param limit  限制数量，可选，默认10，最大100
     * @return 最近预警列表，按触发时间倒序
     */
    @GetMapping("/recent/{symbol}")
    public ApiResponse<List<AlertRecord>> getRecentAlerts(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "10") int limit) {

        log.debug("获取交易对最近预警：交易对={}，限制={}", symbol, limit);

        List<AlertRecord> alerts = alertService.getRecentAlertsBySymbol(symbol, limit);

        return ApiResponse.success(alerts);
    }

    /**
     * 获取未读预警数量
     * <p>
     * 统计所有未读预警的总数，用于前端显示未读提示。
     * <p>
     * 示例请求：{@code GET /api/alerts/unread/count}
     *
     * @return 未读预警数量
     */
    @GetMapping("/unread/count")
    public ApiResponse<Long> getUnreadCount() {
        log.debug("获取未读预警数量");

        Long count = alertService.countUnread();

        return ApiResponse.success(count);
    }

    /**
     * 清理过期预警
     * <p>
     * 删除超过指定天数的预警记录，用于数据清理和维护。
     * <p>
     * 示例请求：{@code DELETE /api/alerts/expired?days=30}
     * <p>
     * 注意：删除操作不可恢复，请谨慎使用。
     *
     * @param days 保留天数，可选，默认30天，表示删除30天前的预警
     * @return 删除结果，包含删除数量和保留天数
     */
    @DeleteMapping("/expired")
    public ApiResponse<Map<String, Object>> deleteExpiredAlerts(
            @RequestParam(defaultValue = "30") int days) {

        log.info("清理过期预警：保留天数={}", days);

        int count = alertService.deleteExpiredAlerts(days);

        Map<String, Object> result = new HashMap<>();
        result.put("deleted", count);
        result.put("keepDays", days);

        return ApiResponse.success(result);
    }
}
