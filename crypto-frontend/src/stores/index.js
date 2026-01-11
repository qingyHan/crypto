import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

/**
 * 全局应用状态管理
 */
export const useAppStore = defineStore('app', () => {
  // WebSocket 连接状态
  const wsConnected = ref(false)
  const wsReconnecting = ref(false)
  
  // 系统状态
  const systemHealth = ref('healthy') // healthy, degraded, offline
  const lastDataUpdate = ref(null)
  
  // 服务状态
  const serviceStatus = ref({
    api: 'unknown',
    flink: 'unknown',
    kafka: 'unknown',
    clickhouse: 'unknown'
  })
  
  // 实时数据
  const realtimePrices = ref({})
  const latestAlerts = ref([])
  const unreadAlertCount = ref(0)
  
  // 计算属性
  const isOnline = computed(() => wsConnected.value || systemHealth.value !== 'offline')
  
  // Actions
  const setWsConnected = (status) => {
    wsConnected.value = status
    if (status) {
      wsReconnecting.value = false
      systemHealth.value = 'healthy'
    }
  }
  
  const setWsReconnecting = (status) => {
    wsReconnecting.value = status
    if (status) {
      systemHealth.value = 'degraded'
    }
  }
  
  const updatePrice = (symbol, priceData) => {
    realtimePrices.value[symbol] = {
      ...priceData,
      updatedAt: Date.now()
    }
    lastDataUpdate.value = new Date()
  }
  
  const addAlert = (alert) => {
    latestAlerts.value.unshift(alert)
    if (latestAlerts.value.length > 50) {
      latestAlerts.value = latestAlerts.value.slice(0, 50)
    }
    if (!alert.isRead) {
      unreadAlertCount.value++
    }
  }
  
  const updateServiceStatus = (service, status) => {
    if (serviceStatus.value[service] !== undefined) {
      serviceStatus.value[service] = status
    }
  }
  
  const setUnreadCount = (count) => {
    unreadAlertCount.value = count
  }
  
  return {
    // State
    wsConnected,
    wsReconnecting,
    systemHealth,
    lastDataUpdate,
    serviceStatus,
    realtimePrices,
    latestAlerts,
    unreadAlertCount,
    // Computed
    isOnline,
    // Actions
    setWsConnected,
    setWsReconnecting,
    updatePrice,
    addAlert,
    updateServiceStatus,
    setUnreadCount
  }
})

/**
 * 市场数据状态管理
 */
export const useMarketStore = defineStore('market', () => {
  const symbols = ref(['BTCUSDT', 'ETHUSDT', 'BNBUSDT', 'SOLUSDT'])
  const selectedSymbol = ref('BTCUSDT')
  const klineInterval = ref('1h')
  
  // 市场数据缓存
  const marketDataCache = ref({})
  const klineDataCache = ref({})
  
  const setSelectedSymbol = (symbol) => {
    selectedSymbol.value = symbol
  }
  
  const setKlineInterval = (interval) => {
    klineInterval.value = interval
  }
  
  const cacheMarketData = (symbol, data) => {
    marketDataCache.value[symbol] = {
      data,
      timestamp: Date.now()
    }
  }
  
  const cacheKlineData = (symbol, interval, data) => {
    const key = `${symbol}_${interval}`
    klineDataCache.value[key] = {
      data,
      timestamp: Date.now()
    }
  }
  
  const getCachedKlineData = (symbol, interval, maxAge = 60000) => {
    const key = `${symbol}_${interval}`
    const cached = klineDataCache.value[key]
    if (cached && Date.now() - cached.timestamp < maxAge) {
      return cached.data
    }
    return null
  }
  
  return {
    symbols,
    selectedSymbol,
    klineInterval,
    marketDataCache,
    klineDataCache,
    setSelectedSymbol,
    setKlineInterval,
    cacheMarketData,
    cacheKlineData,
    getCachedKlineData
  }
})
