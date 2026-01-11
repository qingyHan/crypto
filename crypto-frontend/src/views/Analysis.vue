<template>
  <div class="analysis-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2>数据分析中心</h2>
        <el-tag :type="systemStatus === 'healthy' ? 'success' : 'warning'" size="small">
          {{ systemStatus === 'healthy' ? '数据正常' : '数据延迟' }}
        </el-tag>
      </div>
      <div class="header-right">
        <el-select v-model="selectedSymbol" placeholder="选择交易对" style="width: 140px" @change="loadAnalysisData">
          <el-option v-for="s in symbols" :key="s" :label="s" :value="s" />
        </el-select>
        <el-button type="primary" @click="loadAnalysisData" :loading="loading">
          <el-icon><Refresh /></el-icon>
          刷新数据
        </el-button>
      </div>
    </div>

    <!-- 综合分析报告卡片 -->
    <el-row :gutter="16" class="report-section">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="card-title">📊 {{ selectedSymbol }} 综合分析报告</span>
              <span class="update-time">更新时间: {{ formatTime(lastUpdateTime) }}</span>
            </div>
          </template>
          <el-row :gutter="20">
            <!-- 趋势分析 -->
            <el-col :xs="24" :sm="8">
              <div class="report-item">
                <div class="report-label">趋势方向</div>
                <div class="report-value" :class="trendClass">
                  <el-icon v-if="trendData.direction === 'UP'"><Top /></el-icon>
                  <el-icon v-else-if="trendData.direction === 'DOWN'"><Bottom /></el-icon>
                  <el-icon v-else><Minus /></el-icon>
                  {{ trendText }}
                </div>
                <div class="report-detail">
                  强度: {{ trendData.strength || '-' }} | 
                  变化: {{ formatPercent(trendData.priceChange) }}
                </div>
              </div>
            </el-col>
            <!-- 市场情绪 -->
            <el-col :xs="24" :sm="8">
              <div class="report-item">
                <div class="report-label">市场情绪</div>
                <div class="report-value" :class="sentimentClass">
                  {{ sentimentText }}
                </div>
                <div class="report-detail">
                  情绪指数: {{ sentimentData.score || '-' }} / 100
                </div>
              </div>
            </el-col>
            <!-- 波动率 -->
            <el-col :xs="24" :sm="8">
              <div class="report-item">
                <div class="report-label">波动率</div>
                <div class="report-value">
                  {{ formatVolatility(volatilityData.current) }}
                </div>
                <div class="report-detail">
                  历史平均: {{ formatVolatility(volatilityData.average) }} |
                  <span :class="volatilityData.current > volatilityData.average ? 'text-warning' : 'text-success'">
                    {{ volatilityData.current > volatilityData.average ? '偏高' : '正常' }}
                  </span>
                </div>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <!-- 技术指标详情 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <!-- RSI指标 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="indicator-card">
          <div class="indicator-header">
            <span class="indicator-name">RSI (14)</span>
            <el-tag :type="getRSIStatus(indicators.rsi).type" size="small">
              {{ getRSIStatus(indicators.rsi).text }}
            </el-tag>
          </div>
          <div class="indicator-value">{{ formatNumber(indicators.rsi) }}</div>
          <el-progress 
            :percentage="Math.min(indicators.rsi || 0, 100)" 
            :stroke-width="8"
            :color="getRSIColor(indicators.rsi)"
          />
          <div class="indicator-hint">
            <span>超卖 &lt;30</span>
            <span>超买 &gt;70</span>
          </div>
        </el-card>
      </el-col>

      <!-- MACD指标 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="indicator-card">
          <div class="indicator-header">
            <span class="indicator-name">MACD</span>
            <el-tag :type="indicators.macd?.histogram > 0 ? 'success' : 'danger'" size="small">
              {{ indicators.macd?.histogram > 0 ? '多头' : '空头' }}
            </el-tag>
          </div>
          <div class="macd-values">
            <div class="macd-item">
              <span class="label">DIF</span>
              <span class="value">{{ formatNumber(indicators.macd?.dif) }}</span>
            </div>
            <div class="macd-item">
              <span class="label">DEA</span>
              <span class="value">{{ formatNumber(indicators.macd?.dea) }}</span>
            </div>
            <div class="macd-item">
              <span class="label">柱</span>
              <span class="value" :class="indicators.macd?.histogram > 0 ? 'text-success' : 'text-danger'">
                {{ formatNumber(indicators.macd?.histogram) }}
              </span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 布林带 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="indicator-card">
          <div class="indicator-header">
            <span class="indicator-name">布林带 (20)</span>
            <el-tag :type="getBollingerStatus(indicators.bollinger).type" size="small">
              {{ getBollingerStatus(indicators.bollinger).text }}
            </el-tag>
          </div>
          <div class="bollinger-values">
            <div class="bollinger-item">
              <span class="label">上轨</span>
              <span class="value">{{ formatPrice(indicators.bollinger?.upper) }}</span>
            </div>
            <div class="bollinger-item middle">
              <span class="label">中轨</span>
              <span class="value">{{ formatPrice(indicators.bollinger?.middle) }}</span>
            </div>
            <div class="bollinger-item">
              <span class="label">下轨</span>
              <span class="value">{{ formatPrice(indicators.bollinger?.lower) }}</span>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- KDJ指标 -->
      <el-col :xs="24" :sm="12" :lg="6">
        <el-card shadow="hover" class="indicator-card">
          <div class="indicator-header">
            <span class="indicator-name">KDJ (9,3,3)</span>
            <el-tag :type="getKDJStatus(indicators.kdj).type" size="small">
              {{ getKDJStatus(indicators.kdj).text }}
            </el-tag>
          </div>
          <div class="kdj-values">
            <div class="kdj-item">
              <span class="label">K</span>
              <span class="value">{{ formatNumber(indicators.kdj?.k) }}</span>
            </div>
            <div class="kdj-item">
              <span class="label">D</span>
              <span class="value">{{ formatNumber(indicators.kdj?.d) }}</span>
            </div>
            <div class="kdj-item">
              <span class="label">J</span>
              <span class="value" :class="indicators.kdj?.j > 80 ? 'text-danger' : indicators.kdj?.j < 20 ? 'text-success' : ''">
                {{ formatNumber(indicators.kdj?.j) }}
              </span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 支撑位压力位 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="card-title">📍 支撑位与压力位</span>
            </div>
          </template>
          <div class="levels-container">
            <div class="levels-section resistance">
              <div class="levels-title">压力位 (阻力)</div>
              <div class="level-item" v-for="(level, idx) in levels.resistance" :key="'r'+idx">
                <span class="level-price">${{ formatPrice(typeof level === 'object' ? level.price : level) }}</span>
                <el-progress 
                  :percentage="typeof level === 'object' ? (level.strength || 70) : 70" 
                  :stroke-width="6"
                  color="#ef4444"
                  :show-text="false"
                  style="width: 100px"
                />
                <span class="level-strength">{{ typeof level === 'object' ? level.strength : '强' }}</span>
              </div>
              <div v-if="!levels.resistance?.length" class="no-data">暂无数据</div>
            </div>
            <div class="current-price">
              当前价格: <strong>${{ formatPrice(currentPrice) }}</strong>
            </div>
            <div class="levels-section support">
              <div class="levels-title">支撑位</div>
              <div class="level-item" v-for="(level, idx) in levels.support" :key="'s'+idx">
                <span class="level-price">${{ formatPrice(typeof level === 'object' ? level.price : level) }}</span>
                <el-progress 
                  :percentage="typeof level === 'object' ? (level.strength || 70) : 70" 
                  :stroke-width="6"
                  color="#10b981"
                  :show-text="false"
                  style="width: 100px"
                />
                <span class="level-strength">{{ typeof level === 'object' ? level.strength : '强' }}</span>
              </div>
              <div v-if="!levels.support?.length" class="no-data">暂无数据</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <!-- 相关性分析 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="card-title">🔗 币种相关性矩阵</span>
              <el-tooltip content="基于价格变动计算的皮尔逊相关系数">
                <el-icon><QuestionFilled /></el-icon>
              </el-tooltip>
            </div>
          </template>
          <div class="correlation-matrix">
            <table class="correlation-table">
              <thead>
                <tr>
                  <th></th>
                  <th v-for="s in symbols" :key="s">{{ s.replace('-USDT','') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, i) in correlationMatrix" :key="i">
                  <td class="row-header">{{ symbols[i]?.replace('-USDT','') }}</td>
                  <td v-for="(val, j) in row" :key="j" 
                      :class="getCorrelationClass(val)"
                      :style="{ backgroundColor: getCorrelationColor(val) }">
                    {{ val?.toFixed(2) || '-' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="correlation-legend">
            <span><span class="dot high"></span> 高相关 (&gt;0.8)</span>
            <span><span class="dot medium"></span> 中相关 (0.5-0.8)</span>
            <span><span class="dot low"></span> 低相关 (&lt;0.5)</span>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 价格走势对比 -->
    <el-row :gutter="16" style="margin-top: 16px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <div class="header-left">
                <span class="card-title">📈 多币种价格走势对比</span>
                <el-tag size="small" type="info">以首点价格为基准100%</el-tag>
              </div>
              <div class="header-right">
                <el-checkbox-group v-model="selectedCompareSymbols" size="small">
                  <el-checkbox-button v-for="s in symbols" :key="s" :value="s">
                    {{ s.replace('-USDT','') }}
                  </el-checkbox-button>
                </el-checkbox-group>
              </div>
            </div>
          </template>
          <div ref="priceChartRef" style="height: 400px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { Refresh, Top, Bottom, Minus, QuestionFilled } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { 
  getTrendAnalysis, 
  getTechnicalIndicators, 
  getSupportResistanceLevels,
  getMarketSentiment,
  getVolatilityAnalysis,
  getCorrelationAnalysis 
} from '@/api/analysis'
import { getKlineData } from '@/api/market'

// 状态
const loading = ref(false)
const systemStatus = ref('healthy')
const selectedSymbol = ref('BTC-USDT')
// 统一使用 BASE-QUOTE 格式（与后端/数据库一致）
const symbols = ['BTC-USDT', 'ETH-USDT', 'BNB-USDT', 'SOL-USDT']
const selectedCompareSymbols = ref(['BTC-USDT', 'ETH-USDT', 'BNB-USDT', 'SOL-USDT'])
const lastUpdateTime = ref(new Date())
const currentPrice = ref(0)

// 分析数据
const trendData = reactive({ direction: '', strength: '', priceChange: 0 })
const sentimentData = reactive({ sentiment: '', score: 0 })
const volatilityData = reactive({ current: 0, average: 0 })
const indicators = reactive({ rsi: 0, macd: {}, bollinger: {}, kdj: {} })
const levels = reactive({ support: [], resistance: [] })
const correlationMatrix = ref([])
const priceCompareData = ref({})

// 图表
const priceChartRef = ref(null)
let priceChart = null

// 计算属性
const trendClass = computed(() => {
  const dir = trendData.direction
  if (dir === 'UP' || dir === '上涨') return 'text-success'
  if (dir === 'DOWN' || dir === '下跌') return 'text-danger'
  return 'text-warning'
})

const trendText = computed(() => {
  const dir = trendData.direction
  if (dir === 'UP' || dir === '上涨') return '上涨趋势'
  if (dir === 'DOWN' || dir === '下跌') return '下跌趋势'
  if (dir === 'SIDEWAYS' || dir === '震荡') return '横盘震荡'
  return dir || '分析中...'
})

const sentimentClass = computed(() => {
  const s = sentimentData.sentiment
  if (s === 'BULLISH' || s === '乐观' || s === '看涨') return 'text-success'
  if (s === 'BEARISH' || s === '悲观' || s === '看跌') return 'text-danger'
  return 'text-warning'
})

const sentimentText = computed(() => {
  const s = sentimentData.sentiment
  if (s === 'BULLISH' || s === '乐观' || s === '看涨') return '看涨 📈'
  if (s === 'BEARISH' || s === '悲观' || s === '看跌') return '看跌 📉'
  if (s === 'NEUTRAL' || s === '中性') return '中性 ➡️'
  return s || '分析中...'
})

// 方法
const formatTime = (date) => {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

const formatPercent = (val) => {
  if (val === null || val === undefined) return '-'
  return (val * 100).toFixed(2) + '%'
}

const formatVolatility = (val) => {
  if (val === null || val === undefined || val === 0) return '0.00%'
  // 后端返回的波动率已经是百分比形式的数值
  return Number(val).toFixed(2) + '%'
}

const formatNumber = (val) => {
  if (val === null || val === undefined) return '-'
  return Number(val).toFixed(2)
}

const formatPrice = (val) => {
  if (val === null || val === undefined) return '-'
  return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const getRSIStatus = (rsi) => {
  if (!rsi) return { type: 'info', text: '加载中' }
  if (rsi > 70) return { type: 'danger', text: '超买' }
  if (rsi < 30) return { type: 'success', text: '超卖' }
  return { type: 'warning', text: '中性' }
}

const getRSIColor = (rsi) => {
  if (!rsi) return '#909399'
  if (rsi > 70) return '#ef4444'
  if (rsi < 30) return '#10b981'
  return '#3b82f6'
}

const getBollingerStatus = (bollinger) => {
  if (!bollinger || !bollinger.upper) return { type: 'info', text: '加载中' }
  const price = currentPrice.value
  if (price >= bollinger.upper) return { type: 'danger', text: '触及上轨' }
  if (price <= bollinger.lower) return { type: 'success', text: '触及下轨' }
  return { type: 'warning', text: '区间内' }
}

const getKDJStatus = (kdj) => {
  if (!kdj || !kdj.j) return { type: 'info', text: '加载中' }
  if (kdj.j > 80) return { type: 'danger', text: '超买区' }
  if (kdj.j < 20) return { type: 'success', text: '超卖区' }
  if (kdj.k > kdj.d) return { type: 'success', text: '金叉' }
  return { type: 'danger', text: '死叉' }
}

const getCorrelationClass = (val) => {
  if (val >= 0.8) return 'high-corr'
  if (val >= 0.5) return 'medium-corr'
  return 'low-corr'
}

const getCorrelationColor = (val) => {
  if (val === null || val === undefined) return '#f5f5f5'
  if (val >= 0.8) return 'rgba(59, 130, 246, 0.3)'
  if (val >= 0.5) return 'rgba(59, 130, 246, 0.15)'
  return 'rgba(59, 130, 246, 0.05)'
}

// 加载分析数据
const loadAnalysisData = async () => {
  loading.value = true
  try {
    // 并行请求所有分析数据
    const [trendRes, indicatorsRes, levelsRes, sentimentRes, volatilityRes, correlationRes] = await Promise.allSettled([
      getTrendAnalysis(selectedSymbol.value, 60),
      getTechnicalIndicators(selectedSymbol.value),
      getSupportResistanceLevels(selectedSymbol.value, 24),
      getMarketSentiment(selectedSymbol.value),
      getVolatilityAnalysis(selectedSymbol.value, 24),
      getCorrelationAnalysis(symbols, 24)
    ])

    // 处理趋势数据 - request拦截器已解包，value直接是数据
    if (trendRes.status === 'fulfilled' && trendRes.value) {
      const data = trendRes.value.data || trendRes.value
      trendData.direction = data.direction || data.trend || ''
      trendData.strength = data.strength || 50
      // 计算价格变化: (currentPrice - ma20) / ma20
      if (data.currentPrice && data.ma20) {
        trendData.priceChange = (data.currentPrice - data.ma20) / data.ma20
      } else {
        trendData.priceChange = data.priceChange || data.changePercent || 0
      }
      currentPrice.value = data.currentPrice || 0
    }

    // 处理技术指标
    if (indicatorsRes.status === 'fulfilled' && indicatorsRes.value) {
      const data = indicatorsRes.value.data || indicatorsRes.value
      indicators.rsi = data.rsi || data.RSI || 50
      indicators.macd = data.macd || data.MACD || {}
      indicators.bollinger = data.bollinger || data.bollingerBands || {}
      indicators.kdj = data.kdj || data.KDJ || {}
    }

    // 处理支撑压力位 - 后端返回单个值，转换为数组
    if (levelsRes.status === 'fulfilled' && levelsRes.value) {
      const data = levelsRes.value.data || levelsRes.value
      // 后端返回 { support: number, resistance: number, currentPrice: number }
      levels.support = data.supportLevels || (data.support ? [data.support] : [])
      levels.resistance = data.resistanceLevels || (data.resistance ? [data.resistance] : [])
      if (data.currentPrice) {
        currentPrice.value = data.currentPrice
      }
    }

    // 处理市场情绪 - 后端返回 { sentiment: '乐观', trend: '上涨', rsi: number }
    if (sentimentRes.status === 'fulfilled' && sentimentRes.value) {
      const data = sentimentRes.value.data || sentimentRes.value
      sentimentData.sentiment = data.sentiment || 'NEUTRAL'
      // 根据RSI计算情绪分数 (0-100)
      sentimentData.score = data.score || data.sentimentScore || Math.round(data.rsi || 50)
    }

    // 处理波动率
    if (volatilityRes.status === 'fulfilled' && volatilityRes.value) {
      const data = volatilityRes.value.data || volatilityRes.value
      volatilityData.current = data.currentVolatility || data.volatility || 0
      volatilityData.average = data.averageVolatility || data.average || 0
    }

    // 处理相关性矩阵
    if (correlationRes.status === 'fulfilled' && correlationRes.value) {
      const data = correlationRes.value.data || correlationRes.value
      if (Array.isArray(data.matrix)) {
        correlationMatrix.value = data.matrix
      } else if (data.correlations) {
        // 构建矩阵
        const matrix = symbols.map(() => symbols.map(() => 1))
        correlationMatrix.value = matrix
      }
    }

    lastUpdateTime.value = new Date()
    systemStatus.value = 'healthy'
  } catch (error) {
    console.error('加载分析数据失败:', error)
    systemStatus.value = 'warning'
  } finally {
    loading.value = false
  }
}

// 加载价格对比数据 - 使用1分钟数据
const loadPriceCompareData = async () => {
  const promises = selectedCompareSymbols.value.map(async (symbol) => {
    try {
      // 使用1m间隔，获取最近60个数据点（约1小时）
      const res = await getKlineData(symbol, '1m', { limit: 60 })
      return { symbol, data: res.data || [] }
    } catch {
      return { symbol, data: [] }
    }
  })
  
  const results = await Promise.all(promises)
  const chartData = {}
  results.forEach(r => {
    chartData[r.symbol] = r.data
  })
  priceCompareData.value = chartData
  renderPriceChart()
}

// 渲染价格对比图表
const renderPriceChart = () => {
  if (!priceChartRef.value) return
  
  if (!priceChart) {
    priceChart = echarts.init(priceChartRef.value)
  }

  const colors = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444']
  const series = []
  let xAxisData = []

  selectedCompareSymbols.value.forEach((symbol, idx) => {
    const klines = priceCompareData.value[symbol] || []
    if (klines.length === 0) return

    // 以第一个价格为基准计算百分比
    const basePrice = klines[0]?.close || klines[0]?.closePrice || 1
    const data = klines.map(k => {
      const price = k.close || k.closePrice || 0
      return ((price / basePrice - 1) * 100).toFixed(2)
    })

    if (xAxisData.length === 0) {
      xAxisData = klines.map(k => {
        const time = new Date(k.openTime || k.time)
        return time.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
      })
    }

    series.push({
      name: symbol.replace('-USDT', ''),
      type: 'line',
      smooth: true,
      data,
      itemStyle: { color: colors[idx % colors.length] },
      lineStyle: { width: 2 }
    })
  })

  const option = {
    tooltip: {
      trigger: 'axis',
      formatter: (params) => {
        let result = params[0]?.axisValue + '<br/>'
        params.forEach(p => {
          result += `${p.marker} ${p.seriesName}: ${p.value}%<br/>`
        })
        return result
      }
    },
    legend: {
      data: selectedCompareSymbols.value.map(s => s.replace('-USDT', '')),
      top: 5
    },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: xAxisData },
    yAxis: { 
      type: 'value', 
      name: '涨跌幅',
      axisLabel: { formatter: '{value}%' }
    },
    series
  }

  priceChart.setOption(option)
}

// 监听对比币种变化
watch(selectedCompareSymbols, () => {
  loadPriceCompareData()
})

// 定时刷新
let refreshTimer = null

onMounted(() => {
  loadAnalysisData()
  loadPriceCompareData()
  refreshTimer = setInterval(loadAnalysisData, 60000) // 每分钟刷新
  
  // 监听窗口大小变化
  window.addEventListener('resize', () => {
    priceChart?.resize()
  })
})

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
  priceChart?.dispose()
})
</script>

<style scoped>
.analysis-view {
  padding: 0;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px 20px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1e293b;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.update-time {
  font-size: 12px;
  color: #94a3b8;
}

/* 综合报告 */
.report-item {
  text-align: center;
  padding: 16px;
  border-radius: 8px;
  background: #f8fafc;
}

.report-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.report-value {
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.report-detail {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 8px;
}

/* 指标卡片 */
.indicator-card {
  height: 180px;
}

.indicator-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.indicator-name {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.indicator-value {
  font-size: 32px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 12px;
}

.indicator-hint {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #94a3b8;
  margin-top: 8px;
}

/* MACD */
.macd-values, .bollinger-values, .kdj-values {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 12px;
}

.macd-item, .bollinger-item, .kdj-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 12px;
  background: #f8fafc;
  border-radius: 6px;
}

.macd-item .label, .bollinger-item .label, .kdj-item .label {
  font-size: 12px;
  color: #64748b;
}

.macd-item .value, .bollinger-item .value, .kdj-item .value {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}

.bollinger-item.middle {
  background: #e0f2fe;
}

/* 支撑压力位 */
.levels-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.levels-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.levels-title {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  margin-bottom: 4px;
}

.level-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 6px;
}

.level-price {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
  min-width: 100px;
}

.level-strength {
  font-size: 12px;
  color: #64748b;
}

.current-price {
  text-align: center;
  padding: 12px;
  background: #3b82f6;
  color: white;
  border-radius: 6px;
  font-size: 14px;
}

.current-price strong {
  font-size: 18px;
}

.no-data {
  text-align: center;
  color: #94a3b8;
  padding: 12px;
}

/* 相关性矩阵 */
.correlation-matrix {
  overflow-x: auto;
}

.correlation-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.correlation-table th, .correlation-table td {
  padding: 10px;
  text-align: center;
  border: 1px solid #e2e8f0;
}

.correlation-table th {
  background: #f8fafc;
  font-weight: 600;
  color: #334155;
}

.correlation-table .row-header {
  background: #f8fafc;
  font-weight: 600;
  color: #334155;
}

.correlation-legend {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 12px;
  font-size: 12px;
  color: #64748b;
}

.correlation-legend .dot {
  display: inline-block;
  width: 12px;
  height: 12px;
  border-radius: 2px;
  margin-right: 4px;
}

.correlation-legend .dot.high {
  background: rgba(59, 130, 246, 0.3);
}

.correlation-legend .dot.medium {
  background: rgba(59, 130, 246, 0.15);
}

.correlation-legend .dot.low {
  background: rgba(59, 130, 246, 0.05);
}

/* 文字颜色 */
.text-success { color: #10b981; }
.text-danger { color: #ef4444; }
.text-warning { color: #f59e0b; }

/* 响应式 */
@media (max-width: 768px) {
  .page-header {
    flex-direction: column;
    gap: 12px;
  }
  
  .header-right {
    width: 100%;
    justify-content: flex-end;
  }
}
</style>
