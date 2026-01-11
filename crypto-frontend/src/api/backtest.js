import request from '@/utils/request'

/**
 * 策略回测API
 * 对接后端 BacktestController
 */

/**
 * 执行MACD策略回测
 * @param {Object} params - 回测参数
 * @param {string} params.symbol - 交易对
 * @param {string} params.startTime - 开始时间 (ISO格式)
 * @param {string} params.endTime - 结束时间 (ISO格式)
 * @param {string} params.interval - K线周期，如 '1m', '5m', '1h'
 * @param {number} params.initialCapital - 初始资金
 * @param {number} params.fastPeriod - MACD快线周期，默认12
 * @param {number} params.slowPeriod - MACD慢线周期，默认26
 * @param {number} params.signalPeriod - MACD信号线周期，默认9
 */
export const runMACDBacktest = (params) => {
  return request({
    url: '/backtest/macd',
    method: 'post',
    data: params
  })
}

/**
 * 执行RSI策略回测
 * @param {Object} params - 回测参数
 * @param {string} params.symbol - 交易对
 * @param {string} params.startTime - 开始时间 (ISO格式)
 * @param {string} params.endTime - 结束时间 (ISO格式)
 * @param {string} params.interval - K线周期
 * @param {number} params.initialCapital - 初始资金
 * @param {number} params.rsiPeriod - RSI周期，默认14
 * @param {number} params.oversoldThreshold - 超卖阈值，默认30
 * @param {number} params.overboughtThreshold - 超买阈值，默认70
 */
export const runRSIBacktest = (params) => {
  return request({
    url: '/backtest/rsi',
    method: 'post',
    data: params
  })
}

/**
 * 获取可用回测策略列表
 */
export const getAvailableStrategies = () => {
  return request({
    url: '/backtest/strategies',
    method: 'get'
  })
}
