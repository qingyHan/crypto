package com.crypto.api.dto;

import com.crypto.common.enums.AlertSeverity;
import com.crypto.common.enums.AlertType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 预警查询DTO
 * 用于预警列表查询的参数封装
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 交易对符号(支持模糊查询)
     */
    private String symbol;

    /**
     * 预警类型
     */
    private AlertType alertType;

    /**
     * 严重程度
     */
    private AlertSeverity severity;

    /**
     * 是否已读: null-全部, true-已读, false-未读
     */
    private Boolean isRead;

    /**
     * 开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;

    /**
     * 当前页码(从1开始)
     */
    private Long current;

    /**
     * 每页大小
     */
    private Long size;

    /**
     * 排序字段: trigger_time, created_at, severity
     */
    private String orderBy;

    /**
     * 排序方向: asc, desc
     */
    private String orderDirection;

    /**
     * 初始化默认值
     */
    public void initDefaults() {
        if (current == null || current < 1) {
            current = 1L;
        }
        if (size == null || size < 1) {
            size = 20L;
        }
        if (size > 100) {
            size = 100L; // 限制最大每页100条
        }
        if (orderBy == null || orderBy.isEmpty()) {
            orderBy = "trigger_time";
        }
        if (orderDirection == null || orderDirection.isEmpty()) {
            orderDirection = "desc";
        }
    }

    /**
     * 验证时间范围
     *
     * @return true-有效, false-无效
     */
    public boolean isValidTimeRange() {
        if (startTime != null && endTime != null) {
            return !startTime.isAfter(endTime);
        }
        return true;
    }
}
