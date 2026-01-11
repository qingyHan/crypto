package com.crypto.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 策略配置实体类
 * <p>
 * 用于存储预警策略配置信息
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "strategy_config", autoResultMap = true)
public class StrategyConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 策略名称
     */
    @TableField("strategy_name")
    private String strategyName;

    /**
     * 策略类型: PRICE_ALERT, VOLUME_ALERT, WHALE_ALERT, VOLATILITY_ALERT
     */
    @TableField("strategy_type")
    private String strategyType;

    /**
     * 交易对符号(逗号分隔多个,*表示全部)
     */
    @TableField("symbols")
    private String symbols;

    /**
     * 是否启用: 0-禁用, 1-启用
     */
    @TableField("enabled")
    private Boolean enabled;

    /**
     * 参数配置(JSON格式)
     */
    @TableField(value = "parameters", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> parameters;

    /**
     * 触发次数
     */
    @TableField("trigger_count")
    private Long triggerCount;

    /**
     * 最后触发时间
     */
    @TableField("last_trigger_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastTriggerTime;

    /**
     * 描述
     */
    @TableField("description")
    private String description;

    /**
     * 创建时间
     */
    @TableField("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
