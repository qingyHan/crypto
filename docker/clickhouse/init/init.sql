-- ========================================
-- 加密货币异常交易实时预警系统 - ClickHouse初始化脚本
-- ========================================

CREATE DATABASE IF NOT EXISTS crypto_data;

USE crypto_data;

-- ========================================
-- 1. 实时交易明细表
-- ========================================
CREATE TABLE IF NOT EXISTS trade_details (
    event_time DateTime COMMENT '事件时间',
    symbol String COMMENT '交易对',
    trade_id UInt64 COMMENT '交易ID',
    price Decimal(18, 8) COMMENT '成交价格',
    quantity Decimal(18, 8) COMMENT '成交数量',
    amount Decimal(20, 8) COMMENT '成交金额',
    is_buyer_maker UInt8 COMMENT '买方是否挂单方',
    insert_time DateTime DEFAULT now() COMMENT '插入时间'
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(event_time)
ORDER BY (symbol, event_time)
TTL event_time + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

-- ========================================
-- 2. K线数据表
-- ========================================
CREATE TABLE IF NOT EXISTS kline_data (
    symbol String COMMENT '交易对',
    interval String COMMENT '时间周期: 1m, 5m, 15m, 1h, 1d',
    open_time DateTime COMMENT '开盘时间',
    close_time DateTime COMMENT '收盘时间',
    open Decimal(18, 8) COMMENT '开盘价',
    high Decimal(18, 8) COMMENT '最高价',
    low Decimal(18, 8) COMMENT '最低价',
    close Decimal(18, 8) COMMENT '收盘价',
    volume Decimal(18, 8) COMMENT '成交量',
    quote_volume Decimal(20, 8) COMMENT '成交额',
    trades UInt32 COMMENT '成交笔数',
    insert_time DateTime DEFAULT now() COMMENT '插入时间'
) ENGINE = ReplacingMergeTree(insert_time)
PARTITION BY (symbol, interval, toYYYYMM(open_time))
ORDER BY (symbol, interval, open_time)
TTL open_time + INTERVAL 90 DAY
SETTINGS index_granularity = 8192;

-- 创建索引加速查询
ALTER TABLE kline_data ADD INDEX IF NOT EXISTS idx_close_time close_time TYPE minmax GRANULARITY 3;

-- ========================================
-- 3. 交易量分钟统计物化视图
-- ========================================
CREATE MATERIALIZED VIEW IF NOT EXISTS trade_volume_1min
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(minute_time)
ORDER BY (symbol, minute_time)
AS SELECT
    symbol,
    toStartOfMinute(event_time) AS minute_time,
    count() AS trade_count,
    sum(amount) AS total_amount,
    sum(quantity) AS total_quantity
FROM trade_details
GROUP BY symbol, minute_time;

-- ========================================
-- 4. 市场统计表
-- ========================================
CREATE TABLE IF NOT EXISTS market_stats (
    symbol String COMMENT '交易对',
    stat_time DateTime COMMENT '统计时间',
    time_window String COMMENT '时间窗口: 1h, 24h',
    avg_price Decimal(18, 8) COMMENT '平均价格',
    max_price Decimal(18, 8) COMMENT '最高价',
    min_price Decimal(18, 8) COMMENT '最低价',
    total_volume Decimal(20, 8) COMMENT '总成交量',
    total_amount Decimal(20, 8) COMMENT '总成交额',
    trade_count UInt64 COMMENT '交易笔数',
    volatility Decimal(10, 4) COMMENT '波动率',
    insert_time DateTime DEFAULT now()
) ENGINE = MergeTree()
PARTITION BY (symbol, toYYYYMMDD(stat_time))
ORDER BY (symbol, time_window, stat_time)
TTL stat_time + INTERVAL 180 DAY
SETTINGS index_granularity = 8192;

-- ========================================
-- 注意：不插入示例数据，所有数据应由Flink实时采集和聚合生成
-- ========================================

SELECT 'ClickHouse database initialized successfully!' as status;
SELECT 'Tables created, waiting for real-time data from Flink' as info;
