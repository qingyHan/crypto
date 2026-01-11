package com.crypto.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.crypto.common.enums.AlertSeverity;
import com.crypto.common.enums.AlertType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 预警记录实体类 - 系统预警数据模型
 * <p>
 * 本类代表系统生成的一条预警记录，用于存储市场异常情况（如价格暴涨、巨鲸交易等）。
 * 它是系统各组件间传递预警信息的核心载体。
 * </p>
 * 
 * <h3>数据流向：</h3>
 * <p>
 * Flink预警引擎 / 策略监控服务 &rarr; 生成 {@code AlertRecord} &rarr; Kafka (crypto-alerts) &rarr; API服务 &rarr; 持久化MySQL / WebSocket推送前端
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 * @see com.crypto.common.enums.AlertType
 * @see com.crypto.common.enums.AlertSeverity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "alert_records", autoResultMap = true)
public class AlertRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 预警唯一标识
     */
    private String alertId;

    /**
     * 预警类型
     */
    private AlertType alertType;

    /**
     * 交易对符号
     */
    private String symbol;

    /**
     * 严重程度
     */
    private AlertSeverity severity;

    /**
     * 触发时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime triggerTime;

    /**
     * 当前价格
     */
    private BigDecimal currentPrice;

    /**
     * 涨跌幅百分比
     */
    private BigDecimal changePercent;

    /**
     * 预警消息
     */
    private String message;

    /**
     * 元数据(JSON格式)
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;// 在Java中是Map<String, Object>类型，在数据库中是JSON字符串

    /**
     * 关联的策略ID（可为空，非策略触发的预警为空）
     */
    private Long strategyId;

    /**
     * 是否已读: 0-未读, 1-已读
     */
    private Boolean isRead;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 生成预警ID
     * 格式: alert-{timestamp}-{random}
     */
    public static String generateAlertId() {
        return "alert-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 10000);
    }
}
