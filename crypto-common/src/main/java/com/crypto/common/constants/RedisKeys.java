package com.crypto.common.constants;

/**
 * Redis键常量
 * <p>
 * 统一定义Redis Key的前缀和生成规则，避免硬编码。
 * </p>
 * 
 * <h3>Key分类：</h3>
 * <ul>
 *   <li>实时价格：crypto:price:{symbol}</li>
 *   <li>K线数据：crypto:kline:{symbol}:{interval}:latest</li>
 *   <li>预警列表：crypto:alerts:latest</li>
 *   <li>市场统计：crypto:stats:{symbol}:{timeWindow}</li>
 *   <li>交易量MA：crypto:volume:ma:{symbol}:{period}</li>
 * </ul>
 *
 * @author Qingyang Han
 * @since 1.0.0
 */
public final class RedisKeys {

    private RedisKeys() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 实时价格键前缀
     * 格式: crypto:price:{symbol}
     */
    public static final String PRICE_PREFIX = "crypto:price:";

    /**
     * K线数据键前缀
     * 格式: crypto:kline:{symbol}:{interval}:latest
     */
    public static final String KLINE_PREFIX = "crypto:kline:";

    /**
     * 预警列表键
     */
    public static final String ALERTS_LATEST = "crypto:alerts:latest";

    /**
     * 市场统计数据前缀
     * 格式: crypto:stats:{symbol}:{timeWindow}
     */
    public static final String STATS_PREFIX = "crypto:stats:";

    /**
     * 交易量移动平均前缀
     * 格式: crypto:volume:ma:{symbol}:{period}
     */
    public static final String VOLUME_MA_PREFIX = "crypto:volume:ma:";

    /**
     * 构建实时价格键
     */
    public static String buildPriceKey(String symbol) {
        return PRICE_PREFIX + symbol;
    }

    /**
     * 构建K线键
     */
    public static String buildKlineKey(String symbol, String interval) {
        return KLINE_PREFIX + symbol + ":" + interval + ":latest";
    }

    /**
     * 构建统计数据键
     */
    public static String buildStatsKey(String symbol, String timeWindow) {
        return STATS_PREFIX + symbol + ":" + timeWindow;
    }

    /**
     * 构建交易量移动平均键
     */
    public static String buildVolumeMaKey(String symbol, String period) {
        return VOLUME_MA_PREFIX + symbol + ":" + period;
    }
}
