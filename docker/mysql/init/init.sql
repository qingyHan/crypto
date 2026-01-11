-- ========================================
-- 加密货币异常交易实时预警系统 - MySQL初始化脚本
-- 文件编码: UTF-8 (必须以UTF-8格式保存此文件)
-- ========================================

-- 设置客户端字符集，确保中文正确显示
SET NAMES utf8mb4;
SET CHARACTER SET utf8mb4;

CREATE DATABASE IF NOT EXISTS crypto_analysis DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE crypto_analysis;

-- 设置当前会话字符集
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ========================================
-- 1. 预警记录表
-- ========================================
CREATE TABLE IF NOT EXISTS alert_records (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    alert_id VARCHAR(64) NOT NULL UNIQUE COMMENT '预警唯一标识',
    alert_type VARCHAR(32) NOT NULL COMMENT '预警类型',
    symbol VARCHAR(20) NOT NULL COMMENT '交易对',
    severity ENUM(
        'LOW',
        'MEDIUM',
        'HIGH',
        'CRITICAL'
    ) NOT NULL COMMENT '严重程度',
    trigger_time DATETIME(3) NOT NULL COMMENT '触发时间',
    current_price DECIMAL(18, 8) NOT NULL COMMENT '当前价格',
    change_percent DECIMAL(8, 4) COMMENT '涨跌幅百分比',
    message TEXT NOT NULL COMMENT '预警消息',
    metadata JSON COMMENT '元数据',
    strategy_id BIGINT COMMENT '关联的策略ID',
    is_read TINYINT(1) DEFAULT 0 COMMENT '是否已读',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_symbol_time (symbol, trigger_time),
    INDEX idx_alert_type (alert_type),
    INDEX idx_trigger_time (trigger_time),
    INDEX idx_is_read (is_read),
    INDEX idx_severity (severity),
    INDEX idx_strategy_id (strategy_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '预警记录表';

-- ========================================
-- 2. 策略配置表
-- ========================================
CREATE TABLE IF NOT EXISTS strategy_config (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    strategy_name VARCHAR(100) NOT NULL COMMENT '策略名称',
    strategy_type VARCHAR(32) NOT NULL COMMENT '策略类型',
    symbols VARCHAR(200) NOT NULL COMMENT '交易对(逗号分隔多个,*表示全部)',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用',
    parameters JSON NOT NULL COMMENT '策略参数',
    trigger_count BIGINT DEFAULT 0 COMMENT '触发次数',
    last_trigger_time DATETIME COMMENT '最后触发时间',
    description VARCHAR(500) COMMENT '描述',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_symbols (symbols),
    INDEX idx_enabled (enabled),
    INDEX idx_strategy_type (strategy_type)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '策略配置表';

-- ========================================
-- 3. 交易对配置表
-- ========================================
CREATE TABLE IF NOT EXISTS symbol_config (
    id INT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL UNIQUE COMMENT '交易对',
    base_currency VARCHAR(10) NOT NULL COMMENT '基础货币',
    quote_currency VARCHAR(10) NOT NULL COMMENT '报价货币',
    display_name VARCHAR(50) COMMENT '显示名称',
    is_active TINYINT(1) DEFAULT 1 COMMENT '是否激活',
    collect_enabled TINYINT(1) DEFAULT 1 COMMENT '是否采集数据',
    min_price DECIMAL(18, 8) COMMENT '最小价格',
    max_price DECIMAL(18, 8) COMMENT '最大价格',
    price_precision INT DEFAULT 2 COMMENT '价格精度',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_active (is_active),
    INDEX idx_collect_enabled (collect_enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '交易对配置表';

-- ========================================
-- 插入初始数据
-- ========================================
-- INSERT INTO
--     symbol_config (symbol,base_currency,quote_currency,display_name,
--         min_price,max_price,price_precision,is_active,collect_enabled)
-- VALUES ('BTCUSDT','BTC','USDT','比特币/USDT',10000.00,150000.00,2,1,1),
--        ('ETHUSDT','ETH','USDT','以太坊/USDT',1000.00,10000.00,2,1,1),
--        ('BNBUSDT','BNB','USDT','BNB/USDT',100.00,1000.00,2,1,1),
--        ('SOLUSDT','SOL','USDT','Solana/USDT',10.00,500.00,2,1,1)
-- ON DUPLICATE KEY UPDATE
--     base_currency = VALUES(base_currency),
--     display_name = VALUES(display_name),
--     min_price = VALUES(min_price),
--     max_price = VALUES(max_price),
--     is_active = VALUES(is_active),
--     collect_enabled = VALUES(collect_enabled),
--     updated_at = CURRENT_TIMESTAMP;
-- 插入交易对配置（OKX支持的主流币种 - 与docker-compose.yml中的OKX_SYMBOLS一致）
INSERT INTO
    symbol_config (
        symbol,
        base_currency,
        quote_currency,
        display_name,
        min_price,
        max_price,
        price_precision,
        is_active,
        collect_enabled
    )
VALUES (
        'BTC-USDT',
        'BTC',
        'USDT',
        '比特币/USDT',
        10000.00,
        150000.00,
        2,
        1,
        1
    ),
    (
        'ETH-USDT',
        'ETH',
        'USDT',
        '以太坊/USDT',
        1000.00,
        10000.00,
        2,
        1,
        1
    ),
    (
        'BNB-USDT',
        'BNB',
        'USDT',
        'BNB/USDT',
        100.00,
        1000.00,
        2,
        1,
        1
    ),
    (
        'SOL-USDT',
        'SOL',
        'USDT',
        'Solana/USDT',
        10.00,
        500.00,
        2,
        1,
        1
    )
ON DUPLICATE KEY UPDATE
    base_currency = VALUES(base_currency),
    display_name = VALUES(display_name),
    min_price = VALUES(min_price),
    max_price = VALUES(max_price),
    is_active = VALUES(is_active),
    collect_enabled = VALUES(collect_enabled),
    updated_at = CURRENT_TIMESTAMP;

-- 插入默认预警策略配置（4个主流币种 - 与前端默认策略一致）
INSERT INTO
    strategy_config (
        strategy_name,
        strategy_type,
        symbols,
        parameters,
        description,
        enabled
    )
VALUES (
        'BTC 价格暴涨监控',
        'PRICE_SPIKE',
        'BTC-USDT',
        '{"threshold": 5, "timeWindow": 60}',
        '当BTC价格1小时内涨幅超过5%时触发',
        1
    ),
    (
        'ETH 价格暴跌预警',
        'PRICE_DROP',
        'ETH-USDT',
        '{"threshold": 3, "timeWindow": 30}',
        '当ETH价格30分钟内跌幅超过3%时触发',
        1
    ),
    (
        'BNB 交易量异常检测',
        'VOLUME_ANOMALY',
        'BNB-USDT',
        '{"multiplier": 3, "baseWindow": 24}',
        '当BNB交易量超过24小时均值3倍时触发',
        1
    ),
    (
        'SOL 巨鲸交易监控',
        'WHALE_TRADE',
        'SOL-USDT',
        '{"minAmount": 100000}',
        '当SOL单笔交易金额超过10万USDT时触发',
        1
    )
ON DUPLICATE KEY UPDATE
    updated_at = CURRENT_TIMESTAMP;

-- 注意：不再插入示例预警记录，预警应由系统实时检测生成

-- ========================================
-- 创建视图 - 未读预警统计
-- ========================================
CREATE OR REPLACE VIEW v_unread_alerts_summary AS
SELECT
    symbol,
    alert_type,
    severity,
    COUNT(*) as count,
    MAX(trigger_time) as latest_trigger_time
FROM alert_records
WHERE
    is_read = 0
GROUP BY
    symbol,
    alert_type,
    severity
ORDER BY latest_trigger_time DESC;

-- ========================================
-- 创建存储过程 - 清理历史预警
-- ========================================
DELIMITER /
/

CREATE PROCEDURE IF NOT EXISTS sp_cleanup_old_alerts(IN days INT)
BEGIN
    DELETE FROM alert_records
    WHERE created_at < DATE_SUB(NOW(), INTERVAL days DAY);

    SELECT CONCAT('Deleted alerts older than ', days, ' days') as result;
END //

DELIMITER ;

SELECT 'MySQL database initialized successfully!' as status;