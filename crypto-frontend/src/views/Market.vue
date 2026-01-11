<template>
  <div class="market-view">
    <!-- 实时状态栏 -->
    <div class="realtime-status-bar">
      <div class="status-left">
        <span class="live-indicator" :class="{ active: isLive }">
          <span class="live-dot"></span>
          {{ isLive ? 'LIVE' : 'OFFLINE' }}
        </span>
        <span class="update-info">
          <el-icon><Clock /></el-icon>
          最后更新: {{ lastUpdateTime }}
        </span>
        <span class="refresh-countdown">
          下次刷新: {{ refreshCountdown }}s
        </span>
      </div>
      <div class="status-right">
        <el-button-group size="small">
          <el-button :type="autoRefresh ? 'primary' : 'default'" @click="toggleAutoRefresh">
            <el-icon><VideoPlay v-if="!autoRefresh" /><VideoPause v-else /></el-icon>
            {{ autoRefresh ? '暂停' : '自动' }}
          </el-button>
          <el-button type="success" @click="refreshData" :loading="loading">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-button-group>
      </div>
    </div>

    <!-- 市场统计概览 -->
    <div class="market-summary">
      <div class="summary-item">
        <span class="summary-label">监控币种</span>
        <span class="summary-value">{{ marketData.length }}</span>
      </div>
      <div class="summary-item up">
        <span class="summary-label">上涨</span>
        <span class="summary-value">{{ upCount }}</span>
      </div>
      <div class="summary-item down">
        <span class="summary-label">下跌</span>
        <span class="summary-value">{{ downCount }}</span>
      </div>
      <div class="summary-item">
        <span class="summary-label">24H总成交量</span>
        <span class="summary-value">{{ formatVolume(totalVolume) }}</span>
      </div>
    </div>

    <el-card class="market-card-container">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <span class="title">实时行情</span>
            <el-tag size="small" type="info">{{ marketData.length }} 个交易对</el-tag>
          </div>
          <div class="header-right">
            <el-input
              v-model="searchKeyword"
              placeholder="搜索交易对"
              size="small"
              style="width: 150px"
              clearable
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <el-select v-model="sortBy" size="small" style="width: 120px">
              <el-option label="默认排序" value="default" />
              <el-option label="价格升序" value="price-asc" />
              <el-option label="价格降序" value="price-desc" />
              <el-option label="涨幅排序" value="change-desc" />
              <el-option label="跌幅排序" value="change-asc" />
            </el-select>
          </div>
        </div>
      </template>

      <!-- 市场概览 -->
      <div class="market-overview">
        <el-row :gutter="20">
          <el-col
            v-for="item in filteredMarketData"
            :key="item.symbol"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <div 
              class="market-card" 
              :class="[
                (item.change24h || 0) >= 0 ? 'up-border' : 'down-border',
                { 'price-flash': item.priceFlash }
              ]"
            >
              <div class="card-top">
                <div class="symbol-name">
                  <span class="symbol-icon">{{ item.symbol.slice(0, 1) }}</span>
                  {{ item.symbol }}
                </div>
                <el-tag :type="(item.change24h || 0) >= 0 ? 'success' : 'danger'" size="small" effect="dark">
                  {{ (item.change24h || 0) >= 0 ? '+' : '' }}{{ (item.change24h || 0).toFixed(2) }}%
                </el-tag>
              </div>
              
              <div class="price-section">
                <div class="current-price" :class="item.priceDirection">
                  $ {{ formatPrice(item.lastPrice) }}
                  <span class="price-arrow" v-if="item.priceDirection">
                    <el-icon v-if="item.priceDirection === 'up'"><Top /></el-icon>
                    <el-icon v-else><Bottom /></el-icon>
                  </span>
                </div>
                <div class="price-change" :class="(item.change24h || 0) >= 0 ? 'up' : 'down'">
                  <el-icon v-if="(item.change24h || 0) >= 0"><CaretTop /></el-icon>
                  <el-icon v-else><CaretBottom /></el-icon>
                  {{ Math.abs(item.change24h || 0).toFixed(2) }}%
                </div>
              </div>
              
              <div class="stats-grid">
                <div class="stat-item">
                  <span class="stat-label">24H最高</span>
                  <span class="stat-value high">{{ formatPrice(item.high24h || item.lastPrice * 1.02) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">24H最低</span>
                  <span class="stat-value low">{{ formatPrice(item.low24h || item.lastPrice * 0.98) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">24H成交量</span>
                  <span class="stat-value">{{ formatVolume(item.volume24h) }}</span>
                </div>
                <div class="stat-item">
                  <span class="stat-label">成交额</span>
                  <span class="stat-value">{{ formatVolume((item.volume24h || 0) * (item.lastPrice || 0)) }}</span>
                </div>
              </div>
              
              <div class="mini-chart">
                <div class="spark-line" :class="(item.change24h || 0) >= 0 ? 'up-line' : 'down-line'"></div>
              </div>
              
              <el-button type="primary" text @click="viewDetail(item.symbol)" class="detail-btn">
                <el-icon><TrendCharts /></el-icon>
                查看K线图
              </el-button>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-card>

    <!-- K线图 -->
    <el-card class="kline-card">
      <template #header>
        <div class="card-header">
          <span>K线图 - {{ currentSymbol }}</span>
          <div class="controls">
            <el-select v-model="currentSymbol" size="small" style="width: 150px; margin-right: 10px;">
              <el-option
                v-for="item in marketData"
                :key="item.symbol"
                :label="item.symbol"
                :value="item.symbol"
              />
            </el-select>
            <el-select v-model="klineInterval" size="small" style="width: 120px;">
              <el-option label="1分钟" value="1m" />
              <el-option label="5分钟" value="5m" />
              <el-option label="15分钟" value="15m" />
              <el-option label="1小时" value="1h" />
              <el-option label="1天" value="1d" />
            </el-select>
          </div>
        </div>
      </template>
      <v-chart :option="klineChartOption" style="height: 500px" autoresize />
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, watch, computed } from 'vue'
import { getMarketData, getKlineData } from '@/api/market'
import { Clock, Refresh, VideoPlay, VideoPause, Search, Top, Bottom, TrendCharts, CaretTop, CaretBottom } from '@element-plus/icons-vue'

const marketData = ref([])
const currentSymbol = ref('BTC-USDT')
const klineInterval = ref('1m') // 默认1分钟K线
const loading = ref(false)
const autoRefresh = ref(true)
const isLive = ref(false)
const lastUpdateTime = ref('')
const refreshCountdown = ref(10)
const searchKeyword = ref('')
const sortBy = ref('default')
const klineLoading = ref(false)

let refreshTimer = null
let countdownTimer = null
let previousPrices = {}

// 计算属性
const upCount = computed(() => marketData.value.filter(item => (item.change24h || 0) >= 0).length)
const downCount = computed(() => marketData.value.filter(item => (item.change24h || 0) < 0).length)
const totalVolume = computed(() => marketData.value.reduce((sum, item) => sum + (item.volume24h || 0), 0))

const filteredMarketData = computed(() => {
  let result = [...marketData.value]
  
  // 搜索过滤
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toUpperCase()
    result = result.filter(item => item.symbol.includes(keyword))
  }
  
  // 排序
  switch (sortBy.value) {
    case 'price-asc':
      result.sort((a, b) => (a.lastPrice || 0) - (b.lastPrice || 0))
      break
    case 'price-desc':
      result.sort((a, b) => (b.lastPrice || 0) - (a.lastPrice || 0))
      break
    case 'change-desc':
      result.sort((a, b) => (b.change24h || 0) - (a.change24h || 0))
      break
    case 'change-asc':
      result.sort((a, b) => (a.change24h || 0) - (b.change24h || 0))
      break
  }
  
  return result
})

const klineChartOption = ref({
  title: {
    text: 'K线图',
    left: 'center'
  },
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'cross'
    },
    backgroundColor: 'rgba(50, 50, 50, 0.9)',
    textStyle: { color: '#fff' },
    formatter: function(params) {
      let result = '<strong>' + params[0].axisValue + '</strong><br/>'
      params.forEach(param => {
        if (param.seriesName === 'K线' && param.data) {
          // 格式化价格函数：根据价格大小决定小数位数
          const formatPrice = (price) => {
            if (price === null || price === undefined || isNaN(price)) return '-'
            const num = parseFloat(price)
            if (num >= 10000) return num.toLocaleString('en-US', { minimumFractionDigits: 1, maximumFractionDigits: 1 })
            if (num >= 100) return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
            if (num >= 1) return num.toLocaleString('en-US', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
            return num.toLocaleString('en-US', { minimumFractionDigits: 6, maximumFractionDigits: 6 })
          }
          
          // ECharts candlestick tooltip的param.data格式: [index, open, close, low, high]
          // 第一个元素是数据索引，需要跳过
          const data = param.data
          const open = data[1]
          const close = data[2]
          const low = data[3]
          const high = data[4]
          result += `开: $${formatPrice(open)}<br/>`
          result += `收: $${formatPrice(close)}<br/>`
          result += `低: $${formatPrice(low)}<br/>`
          result += `高: $${formatPrice(high)}<br/>`
        } else if (param.seriesName === '成交量') {
          const vol = param.data?.value || param.data || 0
          // 格式化成交量，添加单位说明
          let volStr
          if (vol >= 1e9) {
            volStr = (vol / 1e9).toFixed(2) + ' B'  // 十亿
          } else if (vol >= 1e6) {
            volStr = (vol / 1e6).toFixed(2) + ' M'  // 百万
          } else if (vol >= 1e3) {
            volStr = (vol / 1e3).toFixed(2) + ' K'  // 千
          } else {
            volStr = vol.toFixed(4)  // 小数保留4位（加密货币交易量通常较小）
          }
          result += `成交量: ${volStr} 个<br/>`
        }
      })
      return result
    }
  },
  legend: {
    data: ['K线', '成交量'],
    top: 30
  },
  grid: [
    {
      left: '10%',
      right: '8%',
      top: '12%',
      height: '48%'
    },
    {
      left: '10%',
      right: '8%',
      top: '68%',
      height: '15%'
    }
  ],
  xAxis: [
    {
      type: 'category',
      data: [],
      scale: true,
      boundaryGap: true,
      axisLine: { onZero: false },
      splitLine: { show: false },
      axisLabel: { 
        show: true,  // 显示K线图的x轴标签
        fontSize: 10,
        interval: 'auto'
      }
    },
    {
      type: 'category',
      gridIndex: 1,
      data: [],
      scale: true,
      boundaryGap: true,
      axisLine: { onZero: false },
      axisLabel: { 
        show: false  // 隐藏成交量图的x轴标签，避免重复
      },
      splitLine: { show: false }
    }
  ],
  yAxis: [
    {
      scale: true,
      splitArea: { show: true }
    },
    {
      scale: true,
      gridIndex: 1,
      splitNumber: 3,
      name: '',  // 移除成交量名称，避免与x轴重叠
      nameLocation: 'end',
      nameTextStyle: {
        color: '#666',
        fontSize: 12
      },
      axisLabel: { 
        show: true,
        fontSize: 10,
        color: '#666',
        formatter: (value) => {
          if (value >= 1e9) return (value / 1e9).toFixed(1) + 'B'
          if (value >= 1e6) return (value / 1e6).toFixed(1) + 'M'
          if (value >= 1e3) return (value / 1e3).toFixed(1) + 'K'
          return value.toFixed(0)
        }
      },
      axisLine: { 
        show: true,
        lineStyle: { color: '#e0e0e0' }
      },
      axisTick: { 
        show: true,
        lineStyle: { color: '#e0e0e0' }
      },
      splitLine: { 
        show: true,
        lineStyle: { 
          type: 'dashed',
          color: '#f0f0f0'
        }
      }
    }
  ],
  dataZoom: [
    {
      type: 'inside',
      xAxisIndex: [0, 1],
      start: 60,
      end: 100
    },
    {
      show: true,
      xAxisIndex: [0, 1],
      type: 'slider',
      top: '88%',
      height: 20,
      start: 60,
      end: 100
    }
  ],
  series: [
    {
      name: 'K线',
      type: 'candlestick',
      data: [],
      itemStyle: {
        color: '#ef4444',
        color0: '#10b981',
        borderColor: '#ef4444',
        borderColor0: '#10b981'
      }
    },
    {
      name: '成交量',
      type: 'bar',
      xAxisIndex: 1,
      yAxisIndex: 1,
      data: []
    }
  ]
})

const formatPrice = (price) => {
  return price?.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatVolume = (volume) => {
  if (!volume) return '0'
  if (volume >= 1e9) return (volume / 1e9).toFixed(2) + 'B'
  if (volume >= 1e6) return (volume / 1e6).toFixed(2) + 'M'
  if (volume >= 1e3) return (volume / 1e3).toFixed(2) + 'K'
  return volume.toFixed(2)
}

const updateLastUpdateTime = () => {
  const now = new Date()
  lastUpdateTime.value = now.toLocaleTimeString('zh-CN', { hour12: false })
}

const startCountdown = () => {
  refreshCountdown.value = 3  // 降低到3秒
  if (countdownTimer) clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    if (refreshCountdown.value > 0) {
      refreshCountdown.value--
    }
  }, 1000)
}

const toggleAutoRefresh = () => {
  autoRefresh.value = !autoRefresh.value
  if (autoRefresh.value) {
    startAutoRefresh()
  } else {
    stopAutoRefresh()
  }
}

const startAutoRefresh = () => {
  isLive.value = true
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = setInterval(() => {
    refreshData()
    startCountdown()
  }, 3000)  // 降低到3秒刷新
  startCountdown()
}

const stopAutoRefresh = () => {
  isLive.value = false
  if (refreshTimer) clearInterval(refreshTimer)
  if (countdownTimer) clearInterval(countdownTimer)
}

const refreshData = async () => {
  loading.value = true
  try {
    let data = []
    
    // 首先尝试从后端获取
    try {
      const response = await getMarketData()
      if (Array.isArray(response)) {
        data = response
      } else if (response && typeof response === 'object') {
        if (Array.isArray(response.data)) {
          data = response.data
        } else if (Array.isArray(response.records)) {
          data = response.records
        } else if (response.list && Array.isArray(response.list)) {
          data = response.list
        }
      }
    } catch (e) {
      console.warn('后端市场数据获取失败:', e.message)
    }

    // 检查是否有有效价格数据
    const hasValidPrice = data.some(item => parseFloat(item.price || item.lastPrice || 0) > 0)
    
    // 如果后端没有有效数据，直接从OKX获取
    if (!hasValidPrice) {
      try {
        const symbols = ['BTC-USDT', 'ETH-USDT', 'BNB-USDT', 'SOL-USDT']
        const promises = symbols.map(async (instId) => {
          const url = `https://www.okx.com/api/v5/market/ticker?instId=${instId}`
          const response = await fetch(url)
          const okxData = await response.json()
          if (okxData.code === '0' && okxData.data && okxData.data[0]) {
            const ticker = okxData.data[0]
            const open24h = parseFloat(ticker.open24h)
            const last = parseFloat(ticker.last)
            const change24h = open24h > 0 ? ((last - open24h) / open24h * 100) : 0
            return {
              symbol: instId, 
              price: last,
              lastPrice: last,
              change24h: change24h,
              volume24h: parseFloat(ticker.vol24h),
              high24h: parseFloat(ticker.high24h),
              low24h: parseFloat(ticker.low24h)
            }
          }
          return null
        })
        const results = await Promise.all(promises)
        data = results.filter(r => r !== null)
        // 开发环境日志
        if (import.meta.env.DEV) console.log('从OKX获取市场数据:', data.length, '个交易对')
      } catch (okxError) {
        console.warn('OKX市场数据获取失败:', okxError.message)
      }
    }

    // 统一符号格式为 BTC-USDT，并去重
    const normalizeSymbol = (s) => {
      if (!s) return ''
      s = s.toUpperCase()
      if (s.includes('-')) return s
      if (s.endsWith('USDT')) return s.replace('USDT', '-USDT')
      return s
    }
    
    // 标准化所有数据的symbol并去重
    const symbolMap = new Map()
    data.forEach(item => {
      const normalized = normalizeSymbol(item.symbol)
      if (normalized && !symbolMap.has(normalized)) {
        symbolMap.set(normalized, { ...item, symbol: normalized })
      }
    })
    data = Array.from(symbolMap.values())

    // 如果数据为空或不足4个交易对，添加默认交易对
    const defaultSymbols = ['BTC-USDT', 'ETH-USDT', 'BNB-USDT', 'SOL-USDT']
    const existingSymbols = data.map(item => item.symbol)
    
    for (const symbol of defaultSymbols) {
      if (!existingSymbols.includes(symbol)) {
        // 为缺失的交易对添加占位数据
        data.push({
          symbol: symbol,
          price: 0,
          lastPrice: 0,
          change24h: 0,
          volume24h: 0,
          high24h: 0,
          low24h: 0
        })
      }
    }

    // 处理价格变动动画
    const newData = data.map(item => {
      const prevPrice = previousPrices[item.symbol]
      let priceDirection = null
      let priceFlash = false

      // 使用 price 或 lastPrice（取决于API返回字段）
      const currentPrice = parseFloat(item.price || item.lastPrice || 0)

      if (prevPrice !== undefined && currentPrice !== prevPrice && currentPrice > 0) {
        priceDirection = currentPrice > prevPrice ? 'up' : 'down'
        priceFlash = true
      }

      if (currentPrice > 0) {
        previousPrices[item.symbol] = currentPrice
      }

      return {
        ...item,
        lastPrice: currentPrice,  // 确保使用 lastPrice 字段
        change24h: parseFloat(item.change24h || 0),
        volume24h: parseFloat(item.volume24h || item.volume || 0),
        high24h: parseFloat(item.high24h || currentPrice * 1.02 || 0),
        low24h: parseFloat(item.low24h || currentPrice * 0.98 || 0),
        priceDirection,
        priceFlash
      }
    })

    // 按默认顺序排序（BTC、ETH、BNB、SOL）
    const sortOrder = { 'BTC-USDT': 1, 'ETH-USDT': 2, 'BNB-USDT': 3, 'SOL-USDT': 4 }
    newData.sort((a, b) => (sortOrder[a.symbol] || 99) - (sortOrder[b.symbol] || 99))

    marketData.value = newData
    updateLastUpdateTime()
    isLive.value = newData.some(item => item.lastPrice > 0)

    // 清除闪烁效果
    setTimeout(() => {
      marketData.value = marketData.value.map(item => ({
        ...item,
        priceFlash: false,
        priceDirection: null
      }))
    }, 1000)

  } catch (error) {
    console.error('Failed to fetch market data:', error)
    isLive.value = false
  } finally {
    loading.value = false
  }
}

const fetchKlineData = async () => {
  klineLoading.value = true
  try {
    let klineList = []
    
    // 首先尝试从后端获取
    try {
      const response = await getKlineData(currentSymbol.value, klineInterval.value, { limit: 100 })
      if (response && response.data) {
        if (Array.isArray(response.data)) {
          klineList = response.data
        } else if (response.data.data && Array.isArray(response.data.data)) {
          klineList = response.data.data
        }
      } else if (Array.isArray(response)) {
        klineList = response
      }
    } catch (e) {
      console.warn('后端K线数据获取失败:', e.message)
    }
    
    // 如果后端没有数据，直接从OKX获取
    if (klineList.length === 0) {
      try {
        const instId = currentSymbol.value.replace('USDT', '-USDT')
        const barMap = { '1m': '1m', '5m': '5m', '15m': '15m', '1h': '1H', '1d': '1D' }
        const bar = barMap[klineInterval.value] || '1H'
        const okxUrl = `https://www.okx.com/api/v5/market/candles?instId=${instId}&bar=${bar}&limit=100`
        const okxResponse = await fetch(okxUrl)
        const okxData = await okxResponse.json()
        
        if (okxData.code === '0' && okxData.data && okxData.data.length > 0) {
          // OKX返回格式: [ts, o, h, l, c, vol, volCcy, volCcyQuote, confirm]
          klineList = okxData.data.map(item => ({
            openTime: new Date(parseInt(item[0])).toISOString(),
            open: parseFloat(item[1]),
            high: parseFloat(item[2]),
            low: parseFloat(item[3]),
            close: parseFloat(item[4]),
            volume: parseFloat(item[5])
          })).reverse() // OKX返回的是倒序，需要反转
          if (import.meta.env.DEV) console.log(`${currentSymbol.value} 从OKX获取 ${klineList.length} 条K线数据`)
        }
      } catch (okxError) {
        console.warn('OKX K线数据获取失败:', okxError.message)
      }
    }
    
    if (import.meta.env.DEV) console.log(`K线数据获取: ${currentSymbol.value}, 周期: ${klineInterval.value}, 数据量: ${klineList.length}`)
    
    if (klineList && klineList.length > 0) {
      // 按时间升序排列
      const sortedData = [...klineList].sort((a, b) => {
        const timeA = new Date(a.openTime).getTime()
        const timeB = new Date(b.openTime).getTime()
        return timeA - timeB
      })
      
      const times = sortedData.map(item => {
        // 格式化时间显示 - 转换为本地时间
        const time = item.openTime
        let localDate
        if (typeof time === 'string') {
          // 如果是ISO格式，解析为Date对象（自动转换为本地时间）
          localDate = new Date(time)
        } else if (typeof time === 'number') {
          // 如果是时间戳（毫秒）
          localDate = new Date(time)
        } else {
          localDate = new Date()
        }
        
        // 根据时间周期选择显示格式（使用本地时间）
        if (klineInterval.value === '1d') {
          return localDate.toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' })
        } else if (klineInterval.value === '1h' || klineInterval.value === '4h') {
          return localDate.toLocaleString('zh-CN', { 
            month: '2-digit', 
            day: '2-digit', 
            hour: '2-digit', 
            minute: '2-digit',
            hour12: false
          })
        } else {
          // 1分钟、5分钟、15分钟等
          return localDate.toLocaleTimeString('zh-CN', { 
            hour: '2-digit', 
            minute: '2-digit',
            hour12: false
          })
        }
      })
      
      // K 线数据格式: [open, close, low, high]
      const candlestickData = sortedData.map(item => [
        parseFloat(item.open),
        parseFloat(item.close),
        parseFloat(item.low),
        parseFloat(item.high)
      ])
      
      // 成交量数据，根据涨跌设置颜色
      const volumeData = sortedData.map(item => ({
        value: parseFloat(item.volume),
        itemStyle: {
          color: parseFloat(item.close) >= parseFloat(item.open) ? '#10b981' : '#ef4444'
        }
      }))

      klineChartOption.value.xAxis[0].data = times
      klineChartOption.value.xAxis[1].data = times
      klineChartOption.value.series[0].data = candlestickData
      klineChartOption.value.series[1].data = volumeData
      klineChartOption.value.title.text = `${currentSymbol.value} K线图`
      
      if (import.meta.env.DEV) console.log(`K线图更新成功: ${candlestickData.length}根K线`)
    } else {
      console.warn(`${currentSymbol.value} 暂无K线数据，等待Flink作业生成数据...`)
      // 显示空状态提示
      klineChartOption.value.xAxis[0].data = []
      klineChartOption.value.xAxis[1].data = []
      klineChartOption.value.series[0].data = []
      klineChartOption.value.series[1].data = []
      klineChartOption.value.title.text = `${currentSymbol.value} K线图 (等待数据中...)`
    }
  } catch (error) {
    console.error('Failed to fetch kline data:', error)
    // 显示错误状态
    klineChartOption.value.xAxis[0].data = []
    klineChartOption.value.xAxis[1].data = []
    klineChartOption.value.series[0].data = []
    klineChartOption.value.series[1].data = []
    klineChartOption.value.title.text = `${currentSymbol.value} K线图 (加载失败)`
  } finally {
    klineLoading.value = false
  }
}

const viewDetail = (symbol) => {
  currentSymbol.value = symbol
}

watch([currentSymbol, klineInterval], () => {
  fetchKlineData()
})

onMounted(() => {
  refreshData()
  fetchKlineData()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<style scoped>
.market-view {
  padding: 20px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e8ec 100%);
  min-height: 100vh;
}

/* 实时状态栏 */
.realtime-status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #eaf3fb 0%, #d6eaff 100%);
  color: #2d3a4a;
  padding: 14px 20px;
  border-radius: 8px;
  margin-bottom: 20px;
  box-shadow: 0 2px 8px rgba(111, 168, 220, 0.1);
}

.status-left {
  display: flex;
  align-items: center;
  gap: 24px;
}

.live-indicator {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: bold;
  font-size: 13px;
  padding: 4px 12px;
  background: rgba(255, 255, 255, 0.5);
  border-radius: 20px;
  color: #888;
}

.live-indicator.active {
  color: #10b981;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #888;
}

.live-indicator.active .live-dot {
  background: #10b981;
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.update-info, .refresh-countdown {
  font-size: 14px;
  color: #4a5568;
  display: flex;
  align-items: center;
  gap: 4px;
}

.refresh-countdown {
  color: #e67e22;
  font-weight: 500;
}

/* 市场统计概览 */
.market-summary {
  display: flex;
  gap: 20px;
  margin-bottom: 20px;
}

.summary-item {
  flex: 1;
  background: white;
  padding: 15px 20px;
  border-radius: 10px;
  text-align: center;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.05);
  border-left: 3px solid #1890ff;
}

.summary-item.up {
  border-left-color: #10b981;
}

.summary-item.down {
  border-left-color: #ef4444;
}

.summary-label {
  display: block;
  font-size: 12px;
  color: #666;
  margin-bottom: 5px;
}

.summary-value {
  font-size: 20px;
  font-weight: bold;
  color: #333;
}

.summary-item.up .summary-value {
  color: #10b981;
}

.summary-item.down .summary-value {
  color: #ef4444;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.header-left .title {
  font-size: 16px;
  font-weight: bold;
}

.header-right {
  display: flex;
  gap: 10px;
  align-items: center;
}

.controls {
  display: flex;
  gap: 10px;
  align-items: center;
}

.market-card-container {
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

.market-overview {
  padding: 10px 0;
}

/* 市场卡片 */
.market-card {
  background: white;
  padding: 20px;
  border-radius: 12px;
  transition: all 0.3s ease;
  border-left: 4px solid #1890ff;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
  position: relative;
  overflow: hidden;
}

.market-card.up-border {
  border-left-color: #10b981;
}

.market-card.down-border {
  border-left-color: #ef4444;
}

.market-card.price-flash {
  animation: priceFlash 0.5s ease;
}

@keyframes priceFlash {
  0%, 100% { background-color: white; }
  50% { background-color: rgba(24, 144, 255, 0.1); }
}

.market-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.12);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.symbol-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: bold;
  color: #1a1a2e;
}

.symbol-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1890ff, #722ed1);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: bold;
}

.price-section {
  margin-bottom: 15px;
}

.current-price {
  font-size: 26px;
  font-weight: bold;
  color: #1a1a2e;
  display: flex;
  align-items: center;
  gap: 5px;
  transition: color 0.3s;
}

.current-price.up {
  color: #10b981;
}

.current-price.down {
  color: #ef4444;
}

.price-arrow {
  font-size: 14px;
}

.price-change {
  font-size: 14px;
  font-weight: 500;
  margin-top: 5px;
  display: flex;
  align-items: center;
  gap: 2px;
}

.price-change.up {
  color: #10b981;
}

.price-change.down {
  color: #ef4444;
}

.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin-bottom: 15px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.stat-item {
  display: flex;
  flex-direction: column;
}

.stat-label {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 2px;
}

.stat-value {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.stat-value.high {
  color: #10b981;
}

.stat-value.low {
  color: #ef4444;
}

.mini-chart {
  height: 30px;
  margin-bottom: 10px;
  background: #f8fafc;
  border-radius: 4px;
  overflow: hidden;
}

.spark-line {
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(24, 144, 255, 0.2));
  position: relative;
}

.spark-line::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, 
    transparent 0%, 
    #1890ff 20%, 
    #1890ff 40%,
    #10b981 60%,
    #ef4444 80%,
    #1890ff 100%
  );
  transform: translateY(-50%);
  animation: sparkMove 3s linear infinite;
}

.spark-line.up-line::after {
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.3), #10b981);
}

.spark-line.down-line::after {
  background: linear-gradient(90deg, rgba(239, 68, 68, 0.3), #ef4444);
}

@keyframes sparkMove {
  0% { transform: translateY(-50%) translateX(-100%); }
  100% { transform: translateY(-50%) translateX(100%); }
}

.detail-btn {
  width: 100%;
  justify-content: center;
}

.kline-card {
  margin-top: 20px;
  border-radius: 12px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.08);
}

/* 响应式 */
@media (max-width: 768px) {
  .market-summary {
    flex-wrap: wrap;
  }
  
  .summary-item {
    flex: 1 1 45%;
  }
  
  .realtime-status-bar {
    flex-direction: column;
    gap: 10px;
  }
  
  .status-left {
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>
