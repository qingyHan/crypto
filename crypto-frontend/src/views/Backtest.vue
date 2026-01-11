<template>
  <div class="backtest-view">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2>策略回测</h2>
        <el-tag type="info" size="small">基于历史数据验证交易策略</el-tag>
      </div>
    </div>

    <el-row :gutter="20">
      <!-- 左侧：回测配置 -->
      <el-col :xs="24" :lg="8">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span class="card-title">⚙️ 回测参数配置</span>
            </div>
          </template>
          
          <el-form :model="backtestForm" :rules="formRules" ref="formRef" label-width="100px">
            <!-- 策略选择 -->
            <el-form-item label="回测策略" prop="strategy">
              <el-select v-model="backtestForm.strategy" placeholder="选择策略" style="width: 100%">
                <el-option 
                  v-for="s in strategies" 
                  :key="s.value" 
                  :label="s.label" 
                  :value="s.value"
                  :disabled="s.disabled"
                >
                  <span>{{ s.label }}</span>
                  <el-tag v-if="s.disabled" size="small" type="info" style="margin-left: 8px">开发中</el-tag>
                </el-option>
              </el-select>
            </el-form-item>

            <!-- 交易对 -->
            <el-form-item label="交易对" prop="symbol">
              <el-select v-model="backtestForm.symbol" placeholder="选择交易对" style="width: 100%">
                <el-option v-for="s in symbols" :key="s" :label="s" :value="s" />
              </el-select>
            </el-form-item>

            <!-- 时间范围 -->
            <el-form-item label="开始时间" prop="startTime">
              <el-date-picker
                v-model="backtestForm.startTime"
                type="datetime"
                placeholder="选择开始时间"
                style="width: 100%"
              />
            </el-form-item>

            <el-form-item label="结束时间" prop="endTime">
              <el-date-picker
                v-model="backtestForm.endTime"
                type="datetime"
                placeholder="选择结束时间"
                style="width: 100%"
              />
            </el-form-item>

            <!-- K线周期 -->
            <el-form-item label="K线周期" prop="interval">
              <el-select v-model="backtestForm.interval" style="width: 100%">
                <el-option label="1分钟" value="1m" />
                <el-option label="5分钟" value="5m" />
                <el-option label="15分钟" value="15m" />
                <el-option label="1小时" value="1h" />
              </el-select>
            </el-form-item>

            <!-- 初始资金 -->
            <el-form-item label="初始资金" prop="initialCapital">
              <el-input-number 
                v-model="backtestForm.initialCapital" 
                :min="1000" 
                :max="10000000" 
                :step="1000"
                style="width: 100%"
              />
            </el-form-item>

            <!-- MACD 参数 -->
            <template v-if="backtestForm.strategy === 'MACD'">
              <el-divider>MACD 参数</el-divider>
              <el-form-item label="快线周期">
                <el-input-number v-model="backtestForm.fastPeriod" :min="5" :max="20" style="width: 100%" />
              </el-form-item>
              <el-form-item label="慢线周期">
                <el-input-number v-model="backtestForm.slowPeriod" :min="20" :max="50" style="width: 100%" />
              </el-form-item>
              <el-form-item label="信号线周期">
                <el-input-number v-model="backtestForm.signalPeriod" :min="5" :max="15" style="width: 100%" />
              </el-form-item>
            </template>

            <!-- RSI 参数 -->
            <template v-if="backtestForm.strategy === 'RSI'">
              <el-divider>RSI 参数</el-divider>
              <el-form-item label="RSI周期">
                <el-input-number v-model="backtestForm.rsiPeriod" :min="5" :max="30" style="width: 100%" />
              </el-form-item>
              <el-form-item label="超卖阈值">
                <el-input-number v-model="backtestForm.oversoldThreshold" :min="10" :max="40" style="width: 100%" />
              </el-form-item>
              <el-form-item label="超买阈值">
                <el-input-number v-model="backtestForm.overboughtThreshold" :min="60" :max="90" style="width: 100%" />
              </el-form-item>
            </template>

            <el-form-item>
              <el-button type="primary" @click="runBacktest" :loading="loading" style="width: 100%">
                <el-icon><VideoPlay /></el-icon>
                开始回测
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右侧：回测结果 -->
      <el-col :xs="24" :lg="16">
        <!-- 回测统计 -->
        <el-card shadow="hover" v-if="backtestResult">
          <template #header>
            <div class="card-header">
              <span class="card-title">📊 回测结果统计</span>
              <el-tag :type="backtestResult.totalReturn >= 0 ? 'success' : 'danger'">
                {{ backtestResult.totalReturn >= 0 ? '盈利' : '亏损' }}
              </el-tag>
            </div>
          </template>

          <el-row :gutter="16">
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">总收益率</div>
                <div class="stat-value" :class="backtestResult.totalReturn >= 0 ? 'up' : 'down'">
                  {{ formatPercent(backtestResult.totalReturn) }}
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">最终资金</div>
                <div class="stat-value">
                  ${{ formatNumber(backtestResult.finalCapital) }}
                </div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">交易次数</div>
                <div class="stat-value">{{ backtestResult.totalTrades }}</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">胜率</div>
                <div class="stat-value">{{ formatPercent(backtestResult.winRate) }}</div>
              </div>
            </el-col>
          </el-row>

          <el-row :gutter="16" style="margin-top: 16px">
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">最大回撤</div>
                <div class="stat-value down">{{ formatPercent(backtestResult.maxDrawdown) }}</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">夏普比率</div>
                <div class="stat-value">{{ backtestResult.sharpeRatio?.toFixed(2) || '-' }}</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">盈利交易</div>
                <div class="stat-value up">{{ backtestResult.winningTrades }}</div>
              </div>
            </el-col>
            <el-col :xs="12" :sm="6">
              <div class="stat-card">
                <div class="stat-label">亏损交易</div>
                <div class="stat-value down">{{ backtestResult.losingTrades }}</div>
              </div>
            </el-col>
          </el-row>
        </el-card>

        <!-- 收益曲线 -->
        <el-card shadow="hover" style="margin-top: 16px" v-if="backtestResult">
          <template #header>
            <div class="card-header">
              <span class="card-title">📈 收益曲线</span>
            </div>
          </template>
          <div ref="equityChartRef" style="height: 300px"></div>
        </el-card>

        <!-- 交易记录 -->
        <el-card shadow="hover" style="margin-top: 16px" v-if="backtestResult?.trades?.length">
          <template #header>
            <div class="card-header">
              <span class="card-title">📋 交易记录</span>
              <span class="trade-count">共 {{ backtestResult.trades.length }} 笔交易</span>
            </div>
          </template>
          <el-table 
            :data="backtestResult.trades" 
            stripe 
            height="400" 
            style="width: 100%"
            :header-cell-style="{ background: '#f8fafc', color: '#64748b' }"
          >
            <el-table-column prop="time" label="交易时间" width="180">
              <template #default="{ row }">
                <span style="color: #475569">{{ formatTime(row.time) }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="type" label="方向" width="100" align="center">
              <template #default="{ row }">
                <el-tag 
                  :type="row.type === 'BUY' ? 'success' : 'danger'" 
                  effect="light"
                  round
                >
                  {{ row.type === 'BUY' ? '买入' : '卖出' }}
                </el-tag>
              </template>
            </el-table-column>
            
            <el-table-column prop="price" label="成交价" width="140" align="right">
              <template #default="{ row }">
                <span style="font-family: monospace; font-weight: 600">
                  ${{ formatPrice(row.price) }}
                </span>
              </template>
            </el-table-column>
            
            <el-table-column prop="quantity" label="数量" width="120" align="right">
              <template #default="{ row }">
                <span style="font-family: monospace">{{ row.quantity?.toFixed(4) }}</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="profit" label="盈亏" width="140" align="right">
              <template #default="{ row }">
                <span 
                  v-if="row.type === 'SELL'"
                  :style="{ 
                    color: row.profit > 0 ? '#10b981' : (row.profit < 0 ? '#ef4444' : '#94a3b8'),
                    fontWeight: 'bold',
                    fontFamily: 'monospace'
                  }"
                >
                  {{ row.profit > 0 ? '+' : '' }}{{ formatNumber(row.profit) }}
                </span>
                <span v-else style="color: #cbd5e1">-</span>
              </template>
            </el-table-column>
            
            <el-table-column prop="reason" label="交易信号">
              <template #default="{ row }">
                <span style="font-size: 12px; color: #64748b">{{ row.reason || '策略触发' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 空状态 -->
        <el-card shadow="hover" v-if="!backtestResult" class="empty-state">
          <el-empty description="配置参数后点击开始回测">
            <template #image>
              <el-icon :size="80" color="#94a3b8"><TrendCharts /></el-icon>
            </template>
          </el-empty>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { VideoPlay, TrendCharts } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { runMACDBacktest, runRSIBacktest, getAvailableStrategies } from '@/api/backtest'

// 策略选项
const strategies = ref([
  { label: 'MACD 策略', value: 'MACD', disabled: false },
  { label: 'RSI 策略', value: 'RSI', disabled: false },
  { label: '布林带策略', value: 'BOLLINGER', disabled: true },
  { label: 'KDJ 策略', value: 'KDJ', disabled: true }
])

// 统一使用 BASE-QUOTE 格式（与后端/数据库一致）
const symbols = ['BTC-USDT', 'ETH-USDT', 'BNB-USDT', 'SOL-USDT']

// 表单
const formRef = ref(null)
const loading = ref(false)
const backtestForm = reactive({
  strategy: 'MACD',
  symbol: 'BTC-USDT',
  startTime: new Date(Date.now() - 24 * 60 * 60 * 1000), // 默认24小时前
  endTime: new Date(),
  interval: '1m', // 数据库只有1分钟数据
  initialCapital: 10000,
  // MACD 参数
  fastPeriod: 12,
  slowPeriod: 26,
  signalPeriod: 9,
  // RSI 参数
  rsiPeriod: 14,
  oversoldThreshold: 30,
  overboughtThreshold: 70
})

const formRules = {
  strategy: [{ required: true, message: '请选择策略', trigger: 'change' }],
  symbol: [{ required: true, message: '请选择交易对', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
  interval: [{ required: true, message: '请选择K线周期', trigger: 'change' }],
  initialCapital: [{ required: true, message: '请输入初始资金', trigger: 'blur' }]
}

// 回测结果
const backtestResult = ref(null)

// 图表
const equityChartRef = ref(null)
let equityChart = null

// 格式化方法
const formatPercent = (val) => {
  if (val === null || val === undefined) return '-'
  return (val * 100).toFixed(2) + '%'
}

const formatNumber = (val) => {
  if (val === null || val === undefined) return '-'
  return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatPrice = (val) => {
  if (val === null || val === undefined) return '-'
  return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatTime = (val) => {
  if (!val) return '-'
  return new Date(val).toLocaleString('zh-CN')
}

// 执行回测
const runBacktest = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  if (backtestForm.startTime >= backtestForm.endTime) {
    ElMessage.warning('开始时间必须早于结束时间')
    return
  }

  loading.value = true
  try {
    const params = {
      symbol: backtestForm.symbol,
      startTime: backtestForm.startTime.getTime(), // 毫秒时间戳
      endTime: backtestForm.endTime.getTime(), // 毫秒时间戳
      interval: backtestForm.interval,
      initialCash: backtestForm.initialCapital, // 后端字段名是 initialCash
      strategyParams: {}
    }

    let response
    if (backtestForm.strategy === 'MACD') {
      params.strategyType = 'MACD'
      params.strategyParams = {
        fastPeriod: backtestForm.fastPeriod,
        slowPeriod: backtestForm.slowPeriod,
        signalPeriod: backtestForm.signalPeriod
      }
      response = await runMACDBacktest(params)
    } else if (backtestForm.strategy === 'RSI') {
      params.strategyType = 'RSI'
      params.strategyParams = {
        rsiPeriod: backtestForm.rsiPeriod,
        oversoldThreshold: backtestForm.oversoldThreshold,
        overboughtThreshold: backtestForm.overboughtThreshold
      }
      response = await runRSIBacktest(params)
    }

    if (response?.success) {
      backtestResult.value = response.result
      ElMessage.success(`回测完成，共处理 ${response.dataPoints} 个数据点`)
      renderEquityChart()
    } else {
      ElMessage.error(response?.message || '回测失败')
    }
  } catch (error) {
    console.error('回测错误:', error)
    ElMessage.error('回测请求失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

// 渲染收益曲线
const renderEquityChart = () => {
  if (!equityChartRef.value || !backtestResult.value?.equityCurve) return

  if (!equityChart) {
    equityChart = echarts.init(equityChartRef.value)
  }

  const equityCurve = backtestResult.value.equityCurve || []
  const initialCapital = backtestForm.initialCapital
  const times = equityCurve.map((_, i) => `T+${i}`)
  
  // 找出最大回撤点（可选优化：如果在后端计算好并在equityPoint里返回更好）
  // 这里仅做简单展示优化

  const option = {
    title: {
      text: '资金权益曲线',
      left: 'center',
      textStyle: { fontSize: 14, color: '#64748b' }
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.95)',
      borderColor: '#e2e8f0',
      textStyle: { color: '#1e293b' },
      formatter: (params) => {
        const p = params[0]
        const val = Number(p.value)
        const profit = val - initialCapital
        const profitRate = (profit / initialCapital * 100).toFixed(2)
        const color = profit >= 0 ? '#10b981' : '#ef4444'
        
        return `
          <div style="font-weight:bold; margin-bottom:4px">${p.axisValue}</div>
          <div>总资产: <b>$${val.toLocaleString()}</b></div>
          <div style="color:${color}">
            盈亏: ${profit >= 0 ? '+' : ''}${profit.toLocaleString()} (${profitRate}%)
          </div>
        `
      }
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: '15%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: times,
      axisLine: { lineStyle: { color: '#cbd5e1' } }
    },
    yAxis: {
      type: 'value',
      name: '资金 ($)',
      scale: true, // 不从0开始，自动适应范围
      splitLine: { lineStyle: { type: 'dashed' } },
      axisLabel: {
        formatter: (val) => (val >= 1000 ? (val / 1000).toFixed(1) + 'K' : val)
      }
    },
    series: [
      {
        name: '资金曲线',
        type: 'line',
        smooth: true,
        symbol: 'none', // 默认不显示点，鼠标悬停显示
        data: equityCurve,
        lineStyle: { 
          color: '#3b82f6', 
          width: 3,
          shadowColor: 'rgba(59, 130, 246, 0.5)',
          shadowBlur: 10
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(59, 130, 246, 0.2)' },
            { offset: 1, color: 'rgba(59, 130, 246, 0.01)' }
          ])
        },
        markLine: {
          symbol: ['none', 'none'],
          label: { show: true, position: 'end', formatter: '初始资金' },
          data: [{ yAxis: initialCapital }],
          lineStyle: { color: '#94a3b8', type: 'dashed', width: 1 }
        }
      }
    ]
  }

  equityChart.setOption(option)
}

// 获取可用策略
const loadStrategies = async () => {
  try {
    const res = await getAvailableStrategies()
    // 兼容 ApiResponse 结构: { code: 200, data: [...], ... }
    const strategyList = Array.isArray(res) ? res : (res?.data || [])
    
    if (Array.isArray(strategyList) && strategyList.length > 0) {
      strategies.value = strategyList.map(s => ({
        label: s.name,
        // 后端返回小写id (macd)，前端使用大写 (MACD)
        value: (s.id || s.code).toUpperCase(),
        // 只有显式标记为开发中的才禁用，或者根据描述判断
        disabled: s.description?.includes('开发中') || false
      }))
    }
  } catch (error) {
    console.warn('加载策略列表失败，使用默认列表:', error)
  }
}

onMounted(() => {
  loadStrategies()
  window.addEventListener('resize', () => {
    equityChart?.resize()
  })
})

onUnmounted(() => {
  equityChart?.dispose()
})
</script>

<style scoped>
.backtest-view {
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

.trade-count {
  font-size: 13px;
  color: #64748b;
}

/* 统计卡片 */
.stat-card {
  text-align: center;
  padding: 16px;
  background: #f8fafc;
  border-radius: 8px;
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
}

.stat-value.up {
  color: #10b981;
}

.stat-value.down {
  color: #ef4444;
}

/* 空状态 */
.empty-state {
  min-height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 文字颜色 */
.text-success { color: #10b981; }
.text-danger { color: #ef4444; }

/* 响应式 */
@media (max-width: 992px) {
  .el-col:first-child {
    margin-bottom: 16px;
  }
}
</style>
