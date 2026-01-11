<template>
  <div class="symbol-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2>交易对管理</h2>
        <el-tag type="info" size="large">{{ pagination.total }} 个交易对</el-tag>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="openAddDialog">
          <el-icon><Plus /></el-icon>
          新增交易对
        </el-button>
      </div>
    </div>

    <el-card>
      <template #header>
        <div class="card-header">
          <span>交易对列表</span>
          <div class="controls">
            <el-button
              type="danger"
              :disabled="selectedIds.length === 0"
              @click="handleBatchDelete"
            >
              批量删除 ({{ selectedIds.length }})
            </el-button>
            <el-button @click="loadSymbolConfigs" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选表单 -->
      <el-form :inline="true" :model="queryForm" class="filter-form">
        <el-form-item label="交易对">
          <el-input
            v-model="queryForm.symbol"
            placeholder="搜索交易对"
            clearable
            style="width: 200px"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="queryForm.isActive" placeholder="全部" clearable style="width: 120px">
            <el-option label="启用" :value="true" />
            <el-option label="禁用" :value="false" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 表格 -->
      <el-table
        :data="symbolList"
        stripe
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="symbol" label="交易对" width="150">
          <template #default="{ row }">
            <span class="symbol-tag">{{ row.symbol }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="displayName" label="显示名称" width="150" />
        <el-table-column prop="baseCurrency" label="基础货币" width="100" />
        <el-table-column prop="quoteCurrency" label="计价货币" width="100" />
        <el-table-column prop="isActive" label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              v-model="row.isActive"
              @change="handleToggleStatus(row)"
              :loading="toggleLoading[row.id]"
            />
          </template>
        </el-table-column>
        <el-table-column prop="collectEnabled" label="数据采集" width="100">
          <template #default="{ row }">
            <el-tag :type="row.collectEnabled ? 'success' : 'info'" size="small">
              {{ row.collectEnabled ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="minPrice" label="最小价格" width="120">
          <template #default="{ row }">
            {{ row.minPrice || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="maxPrice" label="最大价格" width="120">
          <template #default="{ row }">
            {{ row.maxPrice || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="pricePrecision" label="价格精度" width="100" />
        <el-table-column prop="createdAt" label="创建时间" width="180" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" @click="openEditDialog(row)" class="action-btn edit-btn">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button type="danger" size="small" @click="handleDelete(row.id)" class="action-btn delete-btn">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <el-pagination
        v-model:current-page="pagination.current"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleQuery"
        @current-change="handleQuery"
      />
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '新增交易对' : '编辑交易对'"
      width="600px"
    >
      <el-form :model="formData" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="交易对符号" prop="symbol">
          <el-input
            v-model="formData.symbol"
            placeholder="例如: BTC-USDT"
            :disabled="dialogMode === 'edit'"
          />
        </el-form-item>
        <el-form-item label="基础货币" prop="baseCurrency">
          <el-input v-model="formData.baseCurrency" placeholder="例如: BTC" />
        </el-form-item>
        <el-form-item label="计价货币" prop="quoteCurrency">
          <el-input v-model="formData.quoteCurrency" placeholder="例如: USDT" />
        </el-form-item>
        <el-form-item label="显示名称" prop="displayName">
          <el-input v-model="formData.displayName" placeholder="例如: Bitcoin" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="formData.isActive" />
        </el-form-item>
        <el-form-item label="数据采集">
          <el-switch v-model="formData.collectEnabled" />
        </el-form-item>
        <el-form-item label="最小价格">
          <el-input-number
            v-model="formData.minPrice"
            :precision="2"
            :step="0.01"
            :min="0"
          />
        </el-form-item>
        <el-form-item label="最大价格">
          <el-input-number
            v-model="formData.maxPrice"
            :precision="2"
            :step="0.01"
            :min="0"
          />
        </el-form-item>
        <el-form-item label="价格精度">
          <el-input-number
            v-model="formData.pricePrecision"
            :min="0"
            :max="8"
            :step="1"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitLoading">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search, Edit, Delete } from '@element-plus/icons-vue'
import {
  querySymbolConfigs,
  addSymbolConfig,
  updateSymbolConfig,
  deleteSymbolConfig,
  toggleSymbolStatus,
  batchDeleteSymbols
} from '@/api/symbolConfig'

const symbolList = ref([])
const selectedIds = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const dialogMode = ref('add') // 'add' or 'edit'
const submitLoading = ref(false)
const toggleLoading = reactive({})
const formRef = ref(null)

const queryForm = reactive({
  symbol: '',
  isActive: null
})

const pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const formData = reactive({
  id: null,
  symbol: '',
  baseCurrency: '',
  quoteCurrency: '',
  displayName: '',
  isActive: true,
  collectEnabled: true,
  minPrice: null,
  maxPrice: null,
  pricePrecision: 2
})

const formRules = {
  symbol: [
    { required: true, message: '请输入交易对符号', trigger: 'blur' }
  ],
  baseCurrency: [
    { required: true, message: '请输入基础货币', trigger: 'blur' }
  ],
  quoteCurrency: [
    { required: true, message: '请输入计价货币', trigger: 'blur' }
  ]
}

const loadSymbolConfigs = async () => {
  try {
    loading.value = true
    const params = {
      current: pagination.current,
      size: pagination.size,
      symbol: queryForm.symbol || undefined,
      isActive: queryForm.isActive
    }
    const response = await querySymbolConfigs(params)
    symbolList.value = response.records || []
    pagination.total = response.total || 0
  } catch (error) {
    console.error('Failed to load symbol configs:', error)
    ElMessage.error('加载失败')
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  pagination.current = 1
  loadSymbolConfigs()
}

const resetQuery = () => {
  queryForm.symbol = ''
  queryForm.isActive = null
  handleQuery()
}

const openAddDialog = () => {
  dialogMode.value = 'add'
  resetFormData()
  dialogVisible.value = true
}

const openEditDialog = (row) => {
  dialogMode.value = 'edit'
  Object.assign(formData, {
    id: row.id,
    symbol: row.symbol,
    baseCurrency: row.baseCurrency,
    quoteCurrency: row.quoteCurrency,
    displayName: row.displayName,
    isActive: row.isActive,
    collectEnabled: row.collectEnabled,
    minPrice: row.minPrice,
    maxPrice: row.maxPrice,
    pricePrecision: row.pricePrecision
  })
  dialogVisible.value = true
}

const resetFormData = () => {
  Object.assign(formData, {
    id: null,
    symbol: '',
    baseCurrency: '',
    quoteCurrency: '',
    displayName: '',
    isActive: true,
    collectEnabled: true,
    minPrice: null,
    maxPrice: null,
    pricePrecision: 2
  })
}

const handleSubmit = async () => {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    try {
      submitLoading.value = true
      if (dialogMode.value === 'add') {
        await addSymbolConfig(formData)
        ElMessage.success('添加成功')
      } else {
        await updateSymbolConfig(formData.id, formData)
        ElMessage.success('更新成功')
      }
      dialogVisible.value = false
      loadSymbolConfigs()
    } catch (error) {
      ElMessage.error(dialogMode.value === 'add' ? '添加失败' : '更新失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这个交易对吗？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteSymbolConfig(id)
    ElMessage.success('删除成功')
    loadSymbolConfigs()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) {
    ElMessage.warning('请选择要删除的交易对')
    return
  }

  try {
    await ElMessageBox.confirm(`确定要删除选中的 ${selectedIds.value.length} 个交易对吗？`, '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await batchDeleteSymbols(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadSymbolConfigs()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

const handleToggleStatus = async (row) => {
  toggleLoading[row.id] = true
  try {
    await toggleSymbolStatus(row.id, row.isActive)
    ElMessage.success(row.isActive ? '已启用' : '已禁用')
  } catch (error) {
    row.isActive = !row.isActive
    ElMessage.error('操作失败')
  } finally {
    toggleLoading[row.id] = false
  }
}

const handleSelectionChange = (selection) => {
  selectedIds.value = selection.map(item => item.id)
}

onMounted(() => {
  loadSymbolConfigs()
})
</script>

<style scoped>
.symbol-management {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-left h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
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

.filter-form {
  margin-bottom: 20px;
}

.symbol-tag {
  font-family: 'Monaco', monospace;
  font-weight: 600;
  color: #1890ff;
  background: #e6f7ff;
  padding: 2px 8px;
  border-radius: 4px;
}

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

.el-pagination {
  margin-top: 20px;
  justify-content: center;
}
</style>
