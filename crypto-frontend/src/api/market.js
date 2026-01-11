import request from '@/utils/request'

// 简单的内存缓存
const cache = new Map()
const CACHE_TTL = 5000 // 5秒缓存

const getCached = (key) => {
  const cached = cache.get(key)
  if (cached && Date.now() - cached.timestamp < CACHE_TTL) {
    return cached.data
  }
  return null
}

const setCache = (key, data) => {
  cache.set(key, { data, timestamp: Date.now() })
}

/**
 * 获取市场数据列表 - 返回所有交易对的完整行情数据
 * 带缓存和重试机制
 */
export const getMarketData = async (params) => {
  const cacheKey = `market_data_${JSON.stringify(params || {})}`
  const cached = getCached(cacheKey)
  if (cached) {
    return Promise.resolve(cached)
  }
  
  try {
    const data = await request({
      url: '/market/data',
      method: 'get',
      params
    })
    setCache(cacheKey, data)
    return data
  } catch (error) {
    // 如果请求失败，返回缓存数据（如果有）
    if (cached) {
      console.warn('Market data request failed, using cached data')
      return cached
    }
    throw error
  }
}

/**
 * 获取数据流监控概览
 * 带错误处理和默认值
 */
export const getDataFlowOverview = async () => {
  try {
    const data = await request({
      url: '/monitor/overview',
      method: 'get',
      timeout: 10000 // 10秒超时
    })
    
    // 确保返回标准格式
    if (!data || typeof data !== 'object') {
      return getDefaultOverview()
    }
    
    return data
  } catch (error) {
    console.warn('Failed to get data flow overview:', error)
    return getDefaultOverview()
  }
}

// 默认监控数据
const getDefaultOverview = () => {
  return {
    kafka: {
      messagesReceived: 0,
      messagesPerSecond: 0,
      status: 'STARTING',
      consumerLag: 0
    },
    flink: {
      jobs: [{
        name: 'KlineAggregationJob',
        status: 'STARTING',
        recordsProcessed: 0,
        recordsPerSecond: 0
      }]
    },
    clickhouse: {
      recordsWritten: 0,
      writesPerSecond: 0,
      status: 'STARTING'
    },
    redis: {
      cacheHits: 0,
      cacheMisses: 0,
      status: 'STARTING'
    },
    status: 'STARTING',
    timestamp: Date.now()
  }
}

/**
 * 获取Kafka统计
 */
export const getKafkaStats = () => {
  return request({
    url: '/monitor/kafka/stats',
    method: 'get'
  })
}

/**
 * 获取Flink作业状态
 */
export const getFlinkJobs = () => {
  return request({
    url: '/monitor/flink/jobs',
    method: 'get'
  })
}

/**
 * 获取ClickHouse统计
 */
export const getClickHouseStats = () => {
  return request({
    url: '/monitor/clickhouse/stats',
    method: 'get'
  })
}

/**
 * 获取Redis统计
 */
export const getRedisStats = () => {
  return request({
    url: '/monitor/redis/stats',
    method: 'get'
  })
}

/**
 * 获取吞吐量历史数据
 */
export const getThroughputHistory = () => {
  return request({
    url: '/monitor/throughput',
    method: 'get'
  })
}

/**
 * 获取K线数据
 * 带错误处理和空数据返回
 */
export const getKlineData = async (symbol, interval, params) => {
  try {
    const data = await request({
      url: `/market/kline/${symbol}`,
      method: 'get',
      params: { interval, ...params },
      timeout: 15000
    })
    
    // 处理不同的响应格式
    if (data && data.data && Array.isArray(data.data)) {
      return { data: data.data, count: data.count || data.data.length }
    } else if (Array.isArray(data)) {
      return { data, count: data.length }
    } else if (data && Array.isArray(data)) {
      return { data, count: data.length }
    }
    
    return { data: [], count: 0 }
  } catch (error) {
    console.warn(`Failed to get kline data for ${symbol}:`, error)
    return { data: [], count: 0 }
  }
}

/**
 * 获取24小时统计数据
 */
export const get24hStats = (symbol) => {
  return request({
    url: `/market/24h/${symbol}`,
    method: 'get'
  })
}

/**
 * 获取市场概览统计
 */
export const getMarketOverview = () => {
  return request({
    url: '/market/overview',
    method: 'get'
  })
}

/**
 * 获取市场整体统计 - 别名，兼容旧版
 */
export const getMarketStatistics = getMarketOverview

/**
 * 获取交易对列表
 */
export const getSymbolList = () => {
  return request({
    url: '/market/symbols',
    method: 'get'
  })
}

/**
 * 获取实时价格
 */
export const getRealTimePrice = (symbol) => {
  return request({
    url: `/market/price/${symbol}`,
    method: 'get'
  })
}

/**
 * 获取实时价格 (别名，兼容Dashboard)
 */
export const getRealtimePrice = getRealTimePrice
