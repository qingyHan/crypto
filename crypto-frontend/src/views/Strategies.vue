<template>
  <div class="strategies-view">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>预警策略管理</span>
          <div class="controls">
            <el-button type="primary" @click="openStrategyDialog('create')">
              <el-icon><Plus /></el-icon>
              创建新策略
            </el-button>
            <el-button @click="loadStrategies" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 策略列表 -->
      <el-table :data="strategies" stripe style="width: 100%" :header-cell-style="{background: '#f8f9fb', color: '#333', fontWeight: '600'}">
        <el-table-column prop="strategyName" label="策略名称" width="180">
          <template #default="{ row }">
            <span class="strategy-name">{{ row.strategyName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="strategyType" label="策略类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getStrategyTypeTag(row.strategyType)" size="small">
              {{ getStrategyTypeName(row.strategyType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="symbols" label="交易对" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.symbols === '*'" type="info" size="small">全部</el-tag>
            <el-tag v-else type="primary" size="small">{{ row.symbols }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="parameters" label="策略参数" width="180">
          <template #default="{ row }">
            <div class="params-display">
              <template v-if="row.strategyType === 'PRICE_SPIKE' || row.strategyType === 'PRICE_DROP'">
                <span class="param-item">阈值: {{ row.parameters?.threshold || 0 }}%</span>
                <span class="param-item">窗口: {{ row.parameters?.timeWindow || 0 }}分钟</span>
              </template>
              <template v-else-if="row.strategyType === 'VOLUME_ANOMALY'">
                <span class="param-item">倍数: {{ row.parameters?.multiplier || 0 }}x</span>
                <span class="param-item">基准: {{ row.parameters?.baseWindow || 0 }}小时</span>
              </template>
              <template v-else-if="row.strategyType === 'WHALE_TRADE'">
                <span class="param-item">金额: {{ (row.parameters?.minAmount || 0).toLocaleString() }}</span>
              </template>
              <template v-else>
                <span class="param-item">{{ JSON.stringify(row.parameters || {}) }}</span>
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.enabled"
              :active-value="true"
              :inactive-value="false"
              @change="toggleStrategy(row)"
              size="small"
            />
          </template>
        </el-table-column>
        <el-table-column prop="triggerCount" label="触发" width="70" align="center" />
        <el-table-column prop="lastTriggerTime" label="最后触发" width="100" align="center">
          <template #default="{ row }">
            <span class="time-text">{{ row.lastTriggerTime ? formatTime(row.lastTriggerTime) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="desc-text">{{ row.description || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" @click="openStrategyDialog('edit', row)" class="action-btn edit-btn">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" size="small" @click="handleDelete(row)" class="action-btn delete-btn">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 策略编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '创建新策略' : '编辑策略'"
      width="600px"
      @close="resetForm"
    >
      <el-form :model="strategyForm" :rules="formRules" ref="formRef" label-width="120px">
        <el-form-item label="策略名称" prop="strategyName">
          <el-input v-model="strategyForm.strategyName" placeholder="请输入策略名称" />
        </el-form-item>

        <el-form-item label="策略类型" prop="strategyType">
          <el-select v-model="strategyForm.strategyType" placeholder="请选择策略类型" @change="onStrategyTypeChange">
            <el-option label="价格暴涨预警" value="PRICE_SPIKE" />
            <el-option label="价格暴跌预警" value="PRICE_DROP" />
            <el-option label="交易量异常预警" value="VOLUME_ANOMALY" />
            <el-option label="巨鲸交易预警" value="WHALE_TRADE" />
          </el-select>
        </el-form-item>

        <el-form-item label="监控交易对" prop="symbols">
          <el-select v-model="strategyForm.symbols" placeholder="请选择交易对">
            <el-option label="全部交易对" value="*" />
            <el-option label="BTC-USDT" value="BTC-USDT" />
            <el-option label="ETH-USDT" value="ETH-USDT" />
            <el-option label="BNB-USDT" value="BNB-USDT" />
            <el-option label="SOL-USDT" value="SOL-USDT" />
          </el-select>
        </el-form-item>

        <!-- 策略参数 - 根据策略类型动态显示 -->
        <div v-if="strategyForm.strategyType === 'PRICE_SPIKE' || strategyForm.strategyType === 'PRICE_DROP'">
          <el-form-item label="阈值(%)" prop="parameters.threshold">
            <el-input-number
              v-model="strategyForm.parameters.threshold"
              :min="0.1"
              :max="50"
              :step="0.1"
              :precision="1"
            />
            <span class="param-hint">价格变化超过此百分比时触发</span>
          </el-form-item>

          <el-form-item label="时间窗口(分钟)" prop="parameters.timeWindow">
            <el-input-number
              v-model="strategyForm.parameters.timeWindow"
              :min="1"
              :max="1440"
              :step="1"
            />
            <span class="param-hint">检测价格变化的时间范围</span>
          </el-form-item>
        </div>

        <div v-else-if="strategyForm.strategyType === 'VOLUME_ANOMALY'">
          <el-form-item label="倍数" prop="parameters.multiplier">
            <el-input-number
              v-model="strategyForm.parameters.multiplier"
              :min="1"
              :max="10"
              :step="0.1"
              :precision="1"
            />
            <span class="param-hint">交易量超过均值的倍数</span>
          </el-form-item>

          <el-form-item label="基准窗口(小时)" prop="parameters.baseWindow">
            <el-input-number
              v-model="strategyForm.parameters.baseWindow"
              :min="1"
              :max="168"
              :step="1"
            />
            <span class="param-hint">计算交易量均值的时间范围</span>
          </el-form-item>
        </div>

        <div v-else-if="strategyForm.strategyType === 'WHALE_TRADE'">
          <el-form-item label="最小金额(USDT)" prop="parameters.minAmount">
            <el-input-number
              v-model="strategyForm.parameters.minAmount"
              :min="10000"
              :max="10000000"
              :step="10000"
            />
            <span class="param-hint">单笔交易金额阈值</span>
          </el-form-item>
        </div>

        <el-form-item label="策略描述" prop="description">
          <el-input
            v-model="strategyForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入策略描述"
          />
        </el-form-item>

        <el-form-item label="启用策略">
          <el-switch v-model="strategyForm.enabled" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          {{ dialogMode === 'create' ? '创建' : '保存' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { getStrategies, createStrategy, updateStrategy, deleteStrategy, toggleStrategy as toggleStrategyAPI } from '@/api/strategy'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Refresh } from '@element-plus/icons-vue'

// 策略列表
const strategies = ref([])
const loading = ref(false)

// 对话框
const dialogVisible = ref(false)
const dialogMode = ref('create') // 'create' | 'edit'
const submitting = ref(false)

// 表单
const formRef = ref(null)
const strategyForm = reactive({
  id: null,
  strategyName: '',
  strategyType: '',
  symbols: '',
  parameters: {},
  description: '',
  enabled: true
})

// 表单验证规则
const formRules = {
  strategyName: [
    { required: true, message: '请输入策略名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  strategyType: [
    { required: true, message: '请选择策略类型', trigger: 'change' }
  ],
  symbols: [
    { required: true, message: '请选择监控交易对', trigger: 'change' }
  ],
  description: [
    { max: 500, message: '描述不能超过500个字符', trigger: 'blur' }
  ]
}

// 加载策略列表
const loadStrategies = async () => {
  loading.value = true
  try {
    const res = await getStrategies()
    // 后端API返回格式：ApiResponse.success(data) 或直接返回数组
    let data = []
    if (res && res.data) {
      // 如果有data字段，可能是ApiResponse或PageResult格式
      if (Array.isArray(res.data)) {
        data = res.data
      } else if (res.data.records) {
        // PageResult格式
        data = res.data.records
      } else {
        data = []
      }
    } else if (Array.isArray(res)) {
      // 直接返回数组
      data = res
    }
    
    // 处理策略数据，确保parameters是对象
    data = data.map(item => {
      let params = item.parameters
      if (typeof params === 'string') {
        try {
          params = JSON.parse(params)
        } catch (e) {
          console.warn('Failed to parse parameters:', e)
          params = {}
        }
      }
      
      // 转换enabled字段：统一转为boolean
      const enabled = item.enabled === true || item.enabled === 1 || item.enabled === '1'
      
      return {
        ...item,
        parameters: params || {},
        enabled: enabled
      }
    })
    
    // 只显示数据库中的数据，不使用默认策略
    strategies.value = data
    if (data.length === 0) {
      console.info('暂无策略配置，请创建新策略')
    }
  } catch (error) {
    console.error('加载策略列表失败:', error)
    // 加载失败时清空列表，不显示默认策略
    strategies.value = []
    ElMessage.warning('加载策略列表失败，请检查网络连接')
  } finally {
    loading.value = false
  }
}

// 默认策略列表(4个，含3个初始化策略)
const getDefaultStrategies = () => {
  return [
    {
      id: 1,
      strategyName: 'BTC 价格暴涨监控',
      strategyType: 'PRICE_SPIKE',
      symbols: 'BTC-USDT',
      parameters: { threshold: 5, timeWindow: 60 },
      description: '当BTC价格1小时内涨幅超过5%时触发预警',
      enabled: 1,
      triggerCount: 0,
      lastTriggerTime: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    },
    {
      id: 2,
      strategyName: 'ETH 价格暴跌预警',
      strategyType: 'PRICE_DROP',
      symbols: 'ETH-USDT',
      parameters: { threshold: 3, timeWindow: 30 },
      description: '当ETH价格30分钟内跌幅超过3%时触发预警',
      enabled: 1,
      triggerCount: 0,
      lastTriggerTime: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    },
    {
      id: 3,
      strategyName: 'BNB 交易量异常检测',
      strategyType: 'VOLUME_ANOMALY',
      symbols: 'BNB-USDT',
      parameters: { multiplier: 3, baseWindow: 24 },
      description: '当BNB交易量超过24小时均值3倍时触发预警',
      enabled: 1,
      triggerCount: 0,
      lastTriggerTime: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    },
    {
      id: 4,
      strategyName: 'SOL 巨鲸交易监控',
      strategyType: 'WHALE_TRADE',
      symbols: 'SOL-USDT',
      parameters: { minAmount: 100000 },
      description: '当SOL单笔交易金额超过10万USDT时触发预警',
      enabled: 1,
      triggerCount: 0,
      lastTriggerTime: null,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString()
    }
  ]
}

// 打开对话框
const openStrategyDialog = (mode, strategy = null) => {
  dialogMode.value = mode
  if (mode === 'edit' && strategy) {
    strategyForm.id = strategy.id
    strategyForm.strategyName = strategy.strategyName || ''
    strategyForm.strategyType = strategy.strategyType || ''
    strategyForm.symbols = strategy.symbols || ''
    strategyForm.parameters = strategy.parameters ? { ...strategy.parameters } : {}
    strategyForm.description = strategy.description || ''
    strategyForm.enabled = strategy.enabled === 1 || strategy.enabled === true ? 1 : 0
    
    // 如果参数为空，根据策略类型初始化
    if (!strategyForm.parameters || Object.keys(strategyForm.parameters).length === 0) {
      onStrategyTypeChange(strategyForm.strategyType)
    }
  } else {
    // 创建模式，重置表单
    resetForm()
  }
  dialogVisible.value = true
}

// 策略类型改变
const onStrategyTypeChange = (type) => {
  // 根据策略类型初始化参数
  if (type === 'PRICE_SPIKE' || type === 'PRICE_DROP') {
    strategyForm.parameters = {
      threshold: 5,
      timeWindow: 60
    }
  } else if (type === 'VOLUME_ANOMALY') {
    strategyForm.parameters = {
      multiplier: 3,
      baseWindow: 24
    }
  } else if (type === 'WHALE_TRADE') {
    strategyForm.parameters = {
      minAmount: 100000
    }
  }
}

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    
    submitting.value = true
    try {
      // 确保parameters是对象
      let parameters = strategyForm.parameters
      if (!parameters || typeof parameters !== 'object') {
        parameters = {}
      }
      
      // 转换enabled：前端1/0转后端Boolean
      const enabled = strategyForm.enabled === 1 || strategyForm.enabled === true
      
      const data = {
        strategyName: strategyForm.strategyName,
        strategyType: strategyForm.strategyType,
        symbols: strategyForm.symbols,
        parameters: parameters,
        description: strategyForm.description || '',
        enabled: enabled
      }
      
      if (import.meta.env.DEV) console.log('提交策略数据:', data)
      
      if (dialogMode.value === 'create') {
        const result = await createStrategy(data)
        if (import.meta.env.DEV) console.log('创建策略结果:', result)
        ElMessage.success('策略创建成功')
      } else {
        const result = await updateStrategy(strategyForm.id, data)
        if (import.meta.env.DEV) console.log('更新策略结果:', result)
        ElMessage.success('策略更新成功')
      }
      
      dialogVisible.value = false
      await loadStrategies()
    } catch (error) {
      console.error('保存策略失败:', error)
      const errorMsg = error.response?.data?.message || error.message || '保存策略失败'
      ElMessage.error(errorMsg)
    } finally {
      submitting.value = false
    }
  })
}

// 删除策略
const handleDelete = async (strategy) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除策略"${strategy.strategyName}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    if (import.meta.env.DEV) console.log('删除策略:', strategy.id)
    const result = await deleteStrategy(strategy.id)
    if (import.meta.env.DEV) console.log('删除策略结果:', result)
    ElMessage.success('策略删除成功')
    await loadStrategies()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      console.error('删除策略失败:', error)
      const errorMsg = error.response?.data?.message || error.message || '删除策略失败'
      ElMessage.error(errorMsg)
    }
  }
}

// 切换策略状态
const toggleStrategy = async (strategy) => {
  try {
    // 转换enabled：前端1/0转后端Boolean
    const enabled = strategy.enabled === 1 || strategy.enabled === true
    await toggleStrategyAPI(strategy.id, enabled)
    ElMessage.success(enabled ? '策略已启用' : '策略已禁用')
    await loadStrategies()
  } catch (error) {
    console.error('更新策略状态失败:', error)
    const errorMsg = error.response?.data?.message || error.message || '更新策略状态失败'
    ElMessage.error(errorMsg)
    // 回滚状态
    strategy.enabled = strategy.enabled === 1 ? 0 : 1
  }
}

// 重置表单
const resetForm = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  strategyForm.id = null
  strategyForm.strategyName = ''
  strategyForm.strategyType = ''
  strategyForm.symbols = ''
  strategyForm.parameters = {}
  strategyForm.description = ''
  strategyForm.enabled = 1
}

// 工具函数
const getStrategyTypeName = (type) => {
  const types = {
    'PRICE_SPIKE': '价格暴涨',
    'PRICE_DROP': '价格暴跌',
    'VOLUME_ANOMALY': '交易量异常',
    'WHALE_TRADE': '巨鲸交易'
  }
  return types[type] || type
}

const getStrategyTypeTag = (type) => {
  const tags = {
    'PRICE_SPIKE': 'danger',
    'PRICE_DROP': 'warning',
    'VOLUME_ANOMALY': 'success',
    'WHALE_TRADE': 'info'
  }
  return tags[type] || ''
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 初始化
onMounted(() => {
  loadStrategies()
})
</script>

<style scoped lang="scss">
.strategies-view {
  padding: 20px;
  background: #f5f7fa;
  min-height: calc(100vh - 80px);
  
  :deep(.el-card) {
    border-radius: 8px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
    
    .el-card__header {
      background: linear-gradient(135deg, #eaf3fb 0%, #d6eaff 100%);
      color: #2d3a4a;
      padding: 16px 20px;
      border-radius: 8px 8px 0 0;
      
      .card-header {
        span {
          font-weight: 600;
          font-size: 16px;
          color: #2d3a4a;
        }
        
        .controls {
          display: flex;
          gap: 10px;
        }
        
        .el-button--primary {
          background: linear-gradient(135deg, #6fa8dc 0%, #a3bffa 100%);
          border: none;
          color: white;
          
          &:hover {
            background: linear-gradient(135deg, #5a93c7 0%, #8ab0f5 100%);
          }
        }
      }
    }
  }
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  
  .controls {
    display: flex;
    gap: 10px;
  }
  
  :deep(.el-table) {
    th {
      background: #fafafa !important;
      color: #606266;
      font-weight: 600;
    }
    
    .el-table__row {
      transition: all 0.3s;
      
      &:hover {
        background: #f0f9ff !important;
        transform: translateY(-1px);
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
      }
    }
  }
  
  .strategy-name {
    font-weight: 500;
    color: #303133;
  }
  
  .time-text {
    font-size: 12px;
    color: #909399;
  }
  
  .desc-text {
    font-size: 13px;
    color: #606266;
  }
  
  .params-display {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 4px 0;
    
    .param-item {
      font-size: 12px;
      color: #606266;
      background: #f0f5ff;
      padding: 3px 8px;
      border-radius: 4px;
      display: inline-flex;
      align-items: center;
      width: fit-content;
      border-left: 2px solid #1890ff;
    }
  }
  
  .param-hint {
    margin-left: 10px;
    font-size: 12px;
    color: #999;
    font-style: italic;
  }
  
  :deep(.el-dialog) {
    border-radius: 8px;
    
    .el-dialog__header {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      padding: 16px 20px;
      border-radius: 8px 8px 0 0;
      margin: 0;
      
      .el-dialog__title {
        color: white;
        font-weight: 500;
      }
      
      .el-dialog__headerbtn {
        .el-dialog__close {
          color: white;
          
          &:hover {
            color: rgba(255, 255, 255, 0.8);
          }
        }
      }
    }
    
    .el-dialog__body {
      padding: 24px;
    }
    
    .el-dialog__footer {
      padding: 12px 20px;
      background: #fafafa;
      border-radius: 0 0 8px 8px;
    }
  }
  
  :deep(.el-form-item__label) {
    font-weight: 500;
    color: #303133;
  }
  
  :deep(.el-switch.is-checked .el-switch__core) {
    background-color: #52c41a;
    border-color: #52c41a;
  }
  
  :deep(.el-button--link) {
    padding: 0 8px;
    
    &.el-button--primary {
      color: #1890ff;
      
      &:hover {
        color: #40a9ff;
      }
    }
    
    &.el-button--danger {
      color: #ff4d4f;
      
      &:hover {
        color: #ff7875;
      }
    }
  }
  
  /* 操作按钮样式 - 与交易对管理统一 */
  .action-buttons {
    display: flex;
    gap: 8px;
    justify-content: center;
  }

  .action-btn {
    padding: 6px 12px !important;
    border-radius: 6px !important;
    font-size: 13px !important;
    display: inline-flex !important;
    align-items: center !important;
    gap: 4px !important;
  }

  .action-btn .el-icon {
    font-size: 14px;
  }

  .edit-btn {
    background: linear-gradient(135deg, #6fa8dc 0%, #a3bffa 100%) !important;
    border: none !important;
    color: #fff !important;
  }

  .edit-btn:hover {
    background: linear-gradient(135deg, #5a93c7 0%, #8ab0f5 100%) !important;
  }

  .delete-btn {
    background: linear-gradient(135deg, #ffb3b3 0%, #ffd6d6 100%) !important;
    border: none !important;
    color: #d46a6a !important;
  }

  .delete-btn:hover {
    background: linear-gradient(135deg, #ff9999 0%, #ffbfbf 100%) !important;
  }
}
</style>
