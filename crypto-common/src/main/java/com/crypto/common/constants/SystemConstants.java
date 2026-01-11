package com.crypto.common.constants;

/**
 * 系统常量
 * <p>
 * 包含系统通用的常量定义，如日期格式、支持的交易对、预警阈值等。
 * </p>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public final class SystemConstants {

    private SystemConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 日期时间格式
     */
    public static final String DATETIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String TIME_FORMAT = "HH:mm:ss";

    /**
     * 支持的交易对
     */
    public static final String[] SUPPORTED_SYMBOLS = {
            "BTCUSDT", "ETHUSDT", "BNBUSDT", "SOLUSDT",
            "ADAUSDT", "DOGEUSDT", "XRPUSDT", "DOTUSDT"
    };

    /**
     * 支持的K线周期
     */
    public static final String[] SUPPORTED_INTERVALS = {
            "1m", "5m", "15m", "1h", "4h", "1d"
    };

    /**
     * 预警阈值配置
     */
    public static final class AlertThresholds {
        // 价格变化阈值(%)
        public static final double PRICE_CHANGE_THRESHOLD = 3.0;

        // 巨鲸交易金额阈值(USD)
        public static final java.math.BigDecimal WHALE_TRADE_THRESHOLD = new java.math.BigDecimal("1000000.0");

        // 交易量异常倍数阈值
        public static final java.math.BigDecimal VOLUME_MULTIPLIER_THRESHOLD = new java.math.BigDecimal("3.0");

        // 交易量异常倍数(兼容double类型)
        public static final double VOLUME_ANOMALY_MULTIPLIER = 3.0;

        // 波动率异常阈值
        public static final double VOLATILITY_THRESHOLD = 5.0;
    }

    /**
     * 时间窗口配置
     */
    public static final class TimeWindows {
        // 5分钟(毫秒)
        public static final long FIVE_MINUTES = 5 * 60 * 1000L;

        // 15分钟(毫秒)
        public static final long FIFTEEN_MINUTES = 15 * 60 * 1000L;

        // 1小时(毫秒)
        public static final long ONE_HOUR = 60 * 60 * 1000L;

        // 1天(毫秒)
        public static final long ONE_DAY = 24 * 60 * 60 * 1000L;
    }

    /**
     * OKX WebSocket配置
     */
    public static final class OKX {
        // OKX WebSocket地址
        public static final String WS_BASE_URL = "wss://ws.okx.com:8443/ws/v5/public";

        // 交易频道
        public static final String TRADES_CHANNEL = "trades";

        // K线频道
        public static final String KLINE_CHANNEL = "candle";

        // 重连间隔(毫秒)
        public static final long RECONNECT_INTERVAL = 5000L;

        // 心跳间隔(毫秒)
        public static final long PING_INTERVAL = 30000L;
    }
}
