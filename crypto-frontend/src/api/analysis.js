import request from '@/utils/request'

/**
 * 数据分析API
 * 对接后端 AnalysisController
 */

/**
 * 获取趋势分析
 * @param {string} symbol - 交易对，如 BTCUSDT
 * @param {number} period - 分析周期（分钟），默认60
 */
export const getTrendAnalysis = (symbol, period = 60) => {
  return request({
    url: `/analysis/trend/${symbol}`,
    method: 'get',
    params: { period }
  })
}

/**
 * 获取技术指标 (RSI, MACD, 布林带, KDJ)
 * @param {string} symbol - 交易对
 */
export const getTechnicalIndicators = (symbol) => {
  return request({
    url: `/analysis/indicators/${symbol}`,
    method: 'get'
  })
}

/**
 * 检测价格异常
 * @param {string} symbol - 交易对
 * @param {number} window - 检测窗口（分钟），默认30
 */
export const detectAnomalies = (symbol, window = 30) => {
  return request({
    url: `/analysis/anomaly/${symbol}`,
    method: 'get',
    params: { window }
  })
}

/**
 * 获取支撑位和压力位
 * @param {string} symbol - 交易对
 * @param {number} hours - 分析时间窗口（小时），默认24
 */
export const getSupportResistanceLevels = (symbol, hours = 24) => {
  return request({
    url: `/analysis/levels/${symbol}`,
    method: 'get',
    params: { hours }
  })
}

/**
 * 获取市场情绪分析
 * @param {string} symbol - 交易对
 */
export const getMarketSentiment = (symbol) => {
  return request({
    url: `/analysis/sentiment/${symbol}`,
    method: 'get'
  })
}

/**
 * 获取综合分析报告
 * @param {string} symbol - 交易对
 */
export const getComprehensiveReport = (symbol) => {
  return request({
    url: `/analysis/report/${symbol}`,
    method: 'get'
  })
}

/**
 * 批量获取市场概览分析
 * @param {string[]} symbols - 交易对数组
 */
export const batchGetMarketOverview = (symbols) => {
  return request({
    url: '/analysis/overview',
    method: 'get',
    params: { symbols: symbols.join(',') }
  })
}

/**
 * 获取波动率分析
 * @param {string} symbol - 交易对
 * @param {number} hours - 分析时间窗口（小时），默认24
 */
export const getVolatilityAnalysis = (symbol, hours = 24) => {
  return request({
    url: `/analysis/volatility/${symbol}`,
    method: 'get',
    params: { hours }
  })
}

/**
 * 获取相关性分析
 * @param {string[]} symbols - 交易对数组
 * @param {number} hours - 分析时间窗口（小时），默认24
 */
export const getCorrelationAnalysis = (symbols, hours = 24) => {
  return request({
    url: '/analysis/correlation',
    method: 'get',
    params: { 
      symbols: symbols.join(','),
      hours 
    }
  })
}
