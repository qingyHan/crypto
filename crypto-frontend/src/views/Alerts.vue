<template>
  <div class="alerts-view">
    <!-- 预警统计栏 -->
    <div class="alert-stats-bar">
      <div class="stat-item">
        <span class="live-dot active"></span>
        <span class="stat-label">预警监控</span>
        <span class="stat-value live">LIVE</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">总预警</span>
        <span class="stat-value">{{ pagination.total }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">未读</span>
        <span class="stat-value warning">{{ unreadCount }}</span>
      </div>
      <div class="stat-item">
        <span class="stat-label">今日新增</span>
        <span class="stat-value highlight">{{ todayAlerts }}</span>
      </div>
      <div class="stat-item severity-stat">
        <span class="severity-tag critical">严重 {{ severityCounts.CRITICAL }}</span>
        <span class="severity-tag high">高 {{ severityCounts.HIGH }}</span>
        <span class="severity-tag medium">中 {{ severityCounts.MEDIUM }}</span>
        <span class="severity-tag low">低 {{ severityCounts.LOW }}</span>
      </div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>预警管理</span>
          <div class="controls">
            <el-button @click="markAllRead" :disabled="selectedAlerts.length === 0">
              批量标记已读 ({{ selectedAlerts.length }})
            </el-button>
            <el-button type="danger" @click="batchDelete" :disabled="selectedAlerts.length === 0">
              <el-icon><Delete /></el-icon>
              批量删除 ({{ selectedAlerts.length }})
            </el-button>
            <el-button type="warning" plain @click="cleanExpired">
              清理过期预警
            </el-button>
            <el-button type="primary" @click="loadAlerts" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <el-form :inline="true" :model="queryForm" class="filter-form">
        <el-form-item label="交易对">
          <el-select v-model="queryForm.symbol" placeholder="全部" clearable style="width: 130px">
            <el-option label="BTC-USDT" value="BTC-USDT" />
            <el-option label="ETH-USDT" value="ETH-USDT" />
            <el-option label="BNB-USDT" value="BNB-USDT" />
            <el-option label="SOL-USDT" value="SOL-USDT" />
          </el-select>
        </el-form-item>

        <el-form-item label="预警类型">
          <el-select v-model="queryForm.alertType" placeholder="全部" clearable style="width: 130px">
            <el-option label="价格暴涨" value="PRICE_SPIKE" />
            <el-option label="价格暴跌" value="PRICE_DROP" />
            <el-option label="交易量异常" value="VOLUME_ANOMALY" />
            <el-option label="巨鲸交易" value="WHALE_TRADE" />
          </el-select>
        </el-form-item>

        <el-form-item label="严重程度">
          <el-select v-model="queryForm.severity" placeholder="全部" clearable style="width: 100px">
            <el-option label="严重" value="CRITICAL" />
            <el-option label="高" value="HIGH" />
            <el-option label="中" value="MEDIUM" />
            <el-option label="低" value="LOW" />
          </el-select>
        </el-form-item>

        <el-form-item label="阅读状态">
          <el-select v-model="queryForm.isRead" placeholder="全部" clearable style="width: 100px">
            <el-option label="未读" :value="false" />
            <el-option label="已读" :value="true" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 当前筛选条件显示 -->
      <div class="active-filters" v-if="hasActiveFilters">
        <span class="filter-label">当前筛选:</span>
        <el-tag
          v-if="queryForm.symbol"
          closable
          @close="queryForm.symbol = ''; handleQuery()"
        >
          交易对: {{ queryForm.symbol }}
        </el-tag>
        <el-tag
          v-if="queryForm.alertType"
          closable
          @close="queryForm.alertType = ''; handleQuery()"
          type="success"
        >
          类型: {{ getAlertTypeName(queryForm.alertType) }}
        </el-tag>
        <el-tag
          v-if="queryForm.severity"
          closable
          @close="queryForm.severity = ''; handleQuery()"
          :type="getSeverityTagType(queryForm.severity)"
        >
          严重程度: {{ getSeverityName(queryForm.severity) }}
        </el-tag>
        <el-tag
          v-if="queryForm.isRead !== ''"
          closable
          @close="queryForm.isRead = ''; handleQuery()"
          type="info"
        >
          状态: {{ queryForm.isRead ? '已读' : '未读' }}
        </el-tag>
        <el-button size="small" type="text" @click="resetQuery">清空所有筛选</el-button>
      </div>

      <!-- 预警表格 -->
      <el-table
        :data="alertList"
        stripe
        style="width: 100%"
        :row-class-name="getRowClassName"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="symbol" label="交易对" width="120">
          <template #default="{ row }">
            <el-tag>{{ row.symbol }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="alertType" label="预警类型" width="140">
          <template #default="{ row }">
            <el-tag :type="getAlertTypeTag(row.alertType)" size="small">
              {{ getAlertTypeName(row.alertType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="message" label="预警信息" show-overflow-tooltip />
        <el-table-column prop="severity" label="严重程度" width="100">
          <template #default="{ row }">
            <el-tag :type="getSeverityType(row.severity)" effect="dark">
              {{ getSeverityName(row.severity) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="currentPrice" label="当前价格" width="120">
          <template #default="{ row }">
            ${{ formatPrice(row.currentPrice) }}
          </template>
        </el-table-column>
        <el-table-column prop="changePercent" label="涨跌幅" width="100">
          <template #default="{ row }">
            <span v-if="row.changePercent != null" :class="row.changePercent >= 0 ? 'price-up' : 'price-down'">
              {{ row.changePercent >= 0 ? '+' : '' }}{{ row.changePercent }}%
            </span>
            <span v-else class="no-data">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="triggerTime" label="触发时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.triggerTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button v-if="!row.isRead" type="success" size="small" @click="markAsRead(row.id)" class="action-btn read-btn">
                已读
              </el-button>
              <el-button type="primary" size="small" @click="viewDetail(row)" class="action-btn detail-btn">
                详情
              </el-button>
              <el-button type="danger" size="small" @click="handleDelete(row.id)" class="action-btn delete-btn">
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[20, 50, 100, 200]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>

    <!-- 预警详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="预警详情" width="600px">
      <el-descriptions :column="2" border v-if="currentAlert">
        <el-descriptions-item label="交易对">{{ currentAlert.symbol }}</el-descriptions-item>
        <el-descriptions-item label="预警类型">
          <el-tag :type="getAlertTypeTag(currentAlert.alertType)">
            {{ getAlertTypeName(currentAlert.alertType) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="严重程度">
          <el-tag :type="getSeverityType(currentAlert.severity)" effect="dark">
            {{ getSeverityName(currentAlert.severity) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="当前价格">${{ formatPrice(currentAlert.currentPrice) }}</el-descriptions-item>
        <el-descriptions-item label="涨跌幅">
          <span v-if="currentAlert.changePercent != null" :class="currentAlert.changePercent >= 0 ? 'price-up' : 'price-down'">
            {{ currentAlert.changePercent >= 0 ? '+' : '' }}{{ currentAlert.changePercent }}%
          </span>
          <span v-else class="no-data">-</span>
        </el-descriptions-item>
        <el-descriptions-item label="触发时间">{{ formatTime(currentAlert.triggerTime) }}</el-descriptions-item>
        <el-descriptions-item label="预警信息" :span="2">{{ currentAlert.message }}</el-descriptions-item>
        <el-descriptions-item label="元数据" :span="2" v-if="currentAlert.metadata">
          <pre>{{ JSON.stringify(currentAlert.metadata, null, 2) }}</pre>
        </el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { getAlerts, markAlertAsRead, batchMarkAsRead, deleteAlert, batchDeleteAlerts, deleteExpiredAlerts } from '@/api/alert'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Delete, Check, View } from '@element-plus/icons-vue'

// 查询表单
const queryForm = reactive({
  symbol: '',
  alertType: '',
  severity: '',
  isRead: ''
})

// 列表数据
const alertList = ref([])
const selectedAlerts = ref([])
const loading = ref(false)

// 分页
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// 统计数据
const unreadCount = ref(0)
const todayAlerts = ref(0)
const severityCounts = reactive({
  CRITICAL: 0,
  HIGH: 0,
  MEDIUM: 0,
  LOW: 0
})

// 详情对话框
const detailDialogVisible = ref(false)
const currentAlert = ref(null)

// 是否有激活的筛选条件
const hasActiveFilters = computed(() => {
  return queryForm.symbol || queryForm.alertType || queryForm.severity || queryForm.isRead !== ''
})

// 加载预警列表
const loadAlerts = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.page,  // 后端期期current
      size: pagination.size,
      ...queryForm,
      orderBy: 'trigger_time',  // 后端期期orderBy
      orderDirection: 'desc'     // 后端期期orderDirection
    }
    
    // 清除空值参数
    Object.keys(params).forEach(key => {
      if (params[key] === '' || params[key] === null || params[key] === undefined) {
        delete params[key]
      }
    })
    
    const res = await getAlerts(params)
    if (res) {
      alertList.value = res.records || []
      pagination.total = res.total || 0
      
      // 更新统计
      updateStats(alertList.value)
    }
  } catch (error) {
    console.error('加载预警失败:', error)
    ElMessage.error('加载预警数据失败')
  } finally {
    loading.value = false
  }
}

// 更新统计数据
const updateStats = (alerts) => {
  unreadCount.value = alerts.filter(a => !a.isRead).length
  
  // 计算今日预警
  const today = new Date().setHours(0, 0, 0, 0)
  todayAlerts.value = alerts.filter(a => {
    const alertDate = new Date(a.triggerTime).setHours(0, 0, 0, 0)
    return alertDate === today
  }).length
  
  // 严重程度统计
  severityCounts.CRITICAL = alerts.filter(a => a.severity === 'CRITICAL').length
  severityCounts.HIGH = alerts.filter(a => a.severity === 'HIGH').length
  severityCounts.MEDIUM = alerts.filter(a => a.severity === 'MEDIUM').length
  severityCounts.LOW = alerts.filter(a => a.severity === 'LOW').length
}

// 查询
const handleQuery = () => {
  pagination.page = 1
  loadAlerts()
}

// 重置查询
const resetQuery = () => {
  queryForm.symbol = ''
  queryForm.alertType = ''
  queryForm.severity = ''
  queryForm.isRead = ''
  handleQuery()
}

// 分页处理
const handleSizeChange = (size) => {
  pagination.size = size
  loadAlerts()
}

const handlePageChange = (page) => {
  pagination.page = page
  loadAlerts()
}

// 选择处理
const handleSelectionChange = (selection) => {
  selectedAlerts.value = selection
}

// 标记已读
const markAsRead = async (id) => {
  try {
    await markAlertAsRead(id)
    ElMessage.success('标记成功')
    loadAlerts()
  } catch (error) {
    console.error('标记失败:', error)
    ElMessage.error('标记失败')
  }
}

// 批量标记已读
const markAllRead = async () => {
  if (selectedAlerts.value.length === 0) {
    return
  }
  
  try {
    const ids = selectedAlerts.value.map(a => a.id)
    await batchMarkAsRead(ids)
    ElMessage.success(`成功标记 ${ids.length} 条预警为已读`)
    loadAlerts()
  } catch (error) {
    console.error('批量标记失败:', error)
    ElMessage.error('批量标记失败')
  }
}

// 查看详情
const viewDetail = (alert) => {
  currentAlert.value = alert
  detailDialogVisible.value = true
}

// 删除单个预警
const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这条预警吗？', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    await deleteAlert(id)
    ElMessage.success('删除成功')
    loadAlerts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 批量删除
const batchDelete = async () => {
  if (selectedAlerts.value.length === 0) return
  
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedAlerts.value.length} 条预警吗？`,
      '批量删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const ids = selectedAlerts.value.map(a => a.id)
    await batchDeleteAlerts(ids)
    ElMessage.success(`成功删除 ${ids.length} 条预警`)
    loadAlerts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量删除失败:', error)
      ElMessage.error('批量删除失败')
    }
  }
}

// 清理过期预警
const cleanExpired = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要清理30天前的已读预警吗？',
      '清理确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const res = await deleteExpiredAlerts(30)
    ElMessage.success(`成功清理 ${res.deletedCount || 0} 条过期预警`)
    loadAlerts()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('清理失败:', error)
      ElMessage.error('清理过期预警失败')
    }
  }
}

// 工具函数
const getRowClassName = ({ row }) => {
  return !row.isRead ? 'unread-row' : ''
}

const getAlertTypeName = (type) => {
  const types = {
    'PRICE_SPIKE': '价格暴涨',
    'PRICE_DROP': '价格暴跌',
    'VOLUME_ANOMALY': '交易量异常',
    'WHALE_TRADE': '巨鲸交易'
  }
  return types[type] || type
}

const getAlertTypeTag = (type) => {
  const tags = {
    'PRICE_SPIKE': 'danger',
    'PRICE_DROP': 'warning',
    'VOLUME_ANOMALY': 'success',
    'WHALE_TRADE': 'info'
  }
  return tags[type] || ''
}

const getSeverityName = (severity) => {
  const names = {
    'CRITICAL': '严重',
    'HIGH': '高',
    'MEDIUM': '中',
    'LOW': '低'
  }
  return names[severity] || severity
}

const getSeverityType = (severity) => {
  const types = {
    'CRITICAL': 'danger',
    'HIGH': 'warning',
    'MEDIUM': 'info',
    'LOW': 'success'
  }
  return types[severity] || ''
}

const getSeverityTagType = (severity) => {
  return getSeverityType(severity)
}

const formatPrice = (price) => {
  if (!price) return '0.00'
  return Number(price).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const formatTime = (time) => {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  })
}

// 初始化
onMounted(() => {
  loadAlerts()
})
</script>

<style scoped lang="scss">
.alerts-view {
  padding: 20px;
  
  .alert-stats-bar {
    display: flex;
    align-items: center;
    gap: 24px;
    padding: 14px 20px;
    background: linear-gradient(135deg, #eaf3fb 0%, #d6eaff 100%);
    border-radius: 8px;
    margin-bottom: 20px;
    color: #2d3a4a;
    box-shadow: 0 2px 8px rgba(111, 168, 220, 0.1);
    
    .stat-item {
      display: flex;
      align-items: center;
      gap: 8px;
      
      .live-dot {
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #52c41a;
        
        &.active {
          animation: pulse 2s infinite;
        }
      }
      
      .stat-label {
        opacity: 0.8;
        font-size: 14px;
        color: #4a5568;
      }
      
      .stat-value {
        font-size: 18px;
        font-weight: bold;
        color: #2d3a4a;
        
        &.live {
          color: #52c41a;
        }
        
        &.warning {
          color: #e67e22;
        }
        
        &.highlight {
          color: #3182ce;
        }
      }
      
      &.severity-stat {
        margin-left: auto;
        gap: 12px;
        
        .severity-tag {
          padding: 4px 12px;
          border-radius: 12px;
          font-size: 12px;
          font-weight: 500;
          
          &.critical {
            background: rgba(220, 53, 69, 0.15);
            color: #dc3545;
          }
          
          &.high {
            background: rgba(230, 126, 34, 0.15);
            color: #e67e22;
          }
          
          &.medium {
            background: rgba(52, 152, 219, 0.15);
            color: #3498db;
          }
          
          &.low {
            background: rgba(39, 174, 96, 0.15);
            color: #27ae60;
          }
        }
      }
    }
  }
  
  @keyframes pulse {
    0%, 100% {
      opacity: 1;
      box-shadow: 0 0 8px rgba(82, 196, 26, 0.8);
    }
    50% {
      opacity: 0.6;
      box-shadow: 0 0 16px rgba(82, 196, 26, 1);
    }
  }
  
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    
    .controls {
      display: flex;
      gap: 10px;
    }
  }
  
  .filter-form {
    margin-bottom: 20px;
    padding: 15px;
    background: #fafafa;
    border-radius: 4px;
  }
  
  .active-filters {
    margin-bottom: 20px;
    padding: 12px;
    background: #e6f7ff;
    border-radius: 4px;
    display: flex;
    align-items: center;
    gap: 10px;
    flex-wrap: wrap;
    
    .filter-label {
      font-weight: 500;
      color: #1890ff;
    }
  }
  
  .price-up {
    color: #52c41a;
    font-weight: 500;
  }
  
  .price-down {
    color: #f5222d;
    font-weight: 500;
  }

  .no-data {
    color: #999;
    font-weight: 400;
  }
  
  :deep(.unread-row) {
    background-color: #e6f7ff !important;
    font-weight: 500;
  }
  
  pre {
    background: #f5f5f5;
    padding: 10px;
    border-radius: 4px;
    font-size: 12px;
    max-height: 300px;
    overflow: auto;
  }
  
  /* 操作按钮样式 - 与交易对管理统一 */
  .action-buttons {
    display: flex;
    gap: 4px;
    justify-content: center;
    flex-wrap: nowrap;
  }

  .action-btn {
    padding: 4px 8px !important;
    border-radius: 4px !important;
    font-size: 12px !important;
    display: inline-flex !important;
    align-items: center !important;
    min-width: auto;
    justify-content: center;
  }

  .read-btn {
    background: linear-gradient(135deg, #b8eac7 0%, #7ed6a2 100%) !important;
    border: none !important;
    color: #2d4a3a !important;
  }

  .read-btn:hover {
    background: linear-gradient(135deg, #a5e0b5 0%, #6bc88e 100%) !important;
  }

  .detail-btn {
    background: linear-gradient(135deg, #d6eaff 0%, #a3bffa 100%) !important;
    border: none !important;
    color: #2d3a4a !important;
  }

  .detail-btn:hover {
    background: linear-gradient(135deg, #c5deff 0%, #8ab0f5 100%) !important;
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
