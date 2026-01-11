package com.crypto.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 交易对配置实体类
 * <p>
 * 用于存储监控的交易对配置信息
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("symbol_config")
public class SymbolConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 交易对符号
     */
    @TableField("symbol")
    private String symbol;

    /**
     * 基础货币(如BTC)
     */
    @TableField("base_currency")
    private String baseCurrency;

    /**
     * 计价货币(如USDT)
     */
    @TableField("quote_currency")
    private String quoteCurrency;

    /**
     * 显示名称
     */
    @TableField("display_name")
    private String displayName;

    /**
     * 是否激活: 0-禁用, 1-启用
     */
    @TableField("is_active")
    private Boolean isActive;

    /**
     * 是否采集数据: 0-否, 1-是
     */
    @TableField("collect_enabled")
    private Boolean collectEnabled;

    /**
     * 最小价格
     */
    @TableField("min_price")
    private java.math.BigDecimal minPrice;

    /**
     * 最大价格
     */
    @TableField("max_price")
    private java.math.BigDecimal maxPrice;

    /**
     * 价格精度
     */
    @TableField("price_precision")
    private Integer pricePrecision;

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

    /**
     * 获取完整的交易对标识
     *
     * @return 交易对标识(如BTCUSDT)
     */
    public String getFullSymbol() {
        return baseCurrency + quoteCurrency;
    }
}
