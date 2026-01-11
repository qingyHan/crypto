<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapsed ? '64px' : '200px'" class="layout-aside">
      <div class="logo" :class="{ collapsed: isCollapsed }">
        <div class="logo-icon">
          <svg viewBox="0 0 24 24" width="32" height="32" fill="currentColor">
            <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
          </svg>
        </div>
        <transition name="fade">
          <div v-if="!isCollapsed" class="logo-text">
            <span class="logo-title">CryptoGuard</span>
            <span class="logo-subtitle">智能预警平台</span>
          </div>
        </transition>
      </div>
      
      <div class="menu-section">
        <el-menu
          :default-active="activeMenu"
          :router="true"
          :collapse="isCollapsed"
          background-color="transparent"
          text-color="rgba(255,255,255,0.65)"
          active-text-color="#fff"
          :collapse-transition="false"
        >
          <el-menu-item
            v-for="item in menuRoutes"
            :key="item.path"
            :index="item.path"
            class="menu-item-custom"
          >
            <el-icon><component :is="item.meta.icon" /></el-icon>
            <template #title>{{ item.meta.title }}</template>
          </el-menu-item>
        </el-menu>
      </div>
      
      <!-- 系统状态指示 -->
      <div class="system-status" v-if="!isCollapsed">
        <div class="status-dot" :class="systemHealthClass"></div>
        <span class="status-text">{{ systemHealthText }}</span>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-container class="main-container">
      <!-- 头部 -->
      <el-header class="layout-header">
        <div class="header-left">
          <el-button 
            class="collapse-btn" 
            @click="toggleCollapse" 
            text
          >
            <el-icon :size="20">
              <component :is="isCollapsed ? 'Expand' : 'Fold'" />
            </el-icon>
          </el-button>
          <div class="breadcrumb">
            <span class="page-title">{{ currentPageTitle }}</span>
            <span class="page-desc">实时监控 · 数据洞察 · 智能预警</span>
          </div>
        </div>
        <div class="header-right">
          <div class="header-time">
            <el-icon><Clock /></el-icon>
            <span>{{ currentTime }}</span>
          </div>
          <el-tooltip content="预警消息" placement="bottom">
            <el-badge :value="unreadCount" :hidden="unreadCount === 0" class="badge-item">
              <div class="icon-btn" @click="goToAlerts">
                <el-icon :size="18"><BellFilled /></el-icon>
              </div>
            </el-badge>
          </el-tooltip>
          <div class="user-info">
            <el-avatar :size="36" class="user-avatar">
              <el-icon><User /></el-icon>
            </el-avatar>
            <span class="user-name">Admin</span>
          </div>
        </div>
      </el-header>

      <!-- 主体内容 -->
      <el-main class="layout-main">
        <router-view v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getUnreadCount } from '@/api/alert'
import { Clock, User, ArrowDown, Setting, SwitchButton } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const unreadCount = ref(0)
const isCollapsed = ref(false)
const currentTime = ref('')
const systemHealth = ref('healthy') // healthy, warning, error

const menuRoutes = [
  {
    path: '/dashboard',
    meta: { title: '仪表盘', icon: 'DataAnalysis' }
  },
  {
    path: '/market',
    meta: { title: '市场数据', icon: 'TrendCharts' }
  },
  {
    path: '/alerts',
    meta: { title: '预警管理', icon: 'BellFilled' }
  },
  {
    path: '/strategies',
    meta: { title: '策略配置', icon: 'Setting' }
  },
  {
    path: '/analysis',
    meta: { title: '数据分析', icon: 'PieChart' }
  },
  // {
  //   path: '/backtest',
  //   meta: { title: '策略回测', icon: 'Histogram' }
  // },
  {
    path: '/symbol-management',
    meta: { title: '交易对管理', icon: 'Coin' }
  }
]

const activeMenu = computed(() => route.path)
const currentPageTitle = computed(() => route.meta?.title || '概览')

const systemHealthClass = computed(() => ({
  'healthy': systemHealth.value === 'healthy',
  'warning': systemHealth.value === 'warning',
  'error': systemHealth.value === 'error'
}))

const systemHealthText = computed(() => {
  const texts = {
    healthy: '系统运行正常',
    warning: '部分服务延迟',
    error: '服务连接异常'
  }
  return texts[systemHealth.value] || '检测中...'
})

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}

const goToAlerts = () => {
  router.push('/alerts')
}

const updateTime = () => {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  const time = now.toLocaleTimeString('zh-CN', { hour12: false })
  currentTime.value = `${month}-${day} ${time}`
}

const fetchUnreadCount = async () => {
  try {
    const result = await getUnreadCount()
    unreadCount.value = typeof result === 'number' ? result : (result?.count || 0)
    systemHealth.value = 'healthy'
  } catch (error) {
    console.error('Failed to fetch unread count:', error)
    unreadCount.value = 0
    systemHealth.value = 'warning'
  }
}

let timeInterval = null
let refreshInterval = null

onMounted(() => {
  updateTime()
  fetchUnreadCount()
  timeInterval = setInterval(updateTime, 1000)
  refreshInterval = setInterval(fetchUnreadCount, 30000)
})

onUnmounted(() => {
  if (timeInterval) clearInterval(timeInterval)
  if (refreshInterval) clearInterval(refreshInterval)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: #0f1419;
}

.layout-aside {
  background: #1a1f2e;
  overflow: hidden !important;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
  border-right: 1px solid rgba(255, 255, 255, 0.08);
  scrollbar-width: none !important;
  -ms-overflow-style: none !important;
  
  &::-webkit-scrollbar {
    display: none !important;
    width: 0 !important;
    height: 0 !important;
  }
  
  * {
    scrollbar-width: none !important;
    -ms-overflow-style: none !important;
    
    &::-webkit-scrollbar {
      display: none !important;
      width: 0 !important;
      height: 0 !important;
    }
  }
}

.logo {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  gap: 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  transition: all 0.3s ease;
}

.logo.collapsed {
  justify-content: center;
  padding: 0;
}

.logo-icon {
  width: 40px;
  height: 40px;
  min-width: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  flex-shrink: 0;
  box-shadow: 0 4px 12px rgba(59, 130, 246, 0.35);
}

.logo-icon svg {
  width: 24px;
  height: 24px;
}

.logo-text {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  white-space: nowrap;
}

.logo-title {
  font-size: 18px;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 0.5px;
}

.logo-subtitle {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 2px;
}

.menu-section {
  flex: 1;
  padding: 16px 12px;
  overflow-y: auto;
  overflow-x: hidden;
  scrollbar-width: none; /* Firefox */
  -ms-overflow-style: none; /* IE/Edge */
  
  &::-webkit-scrollbar {
    display: none; /* Chrome/Safari */
  }
}

/* 收起时菜单居中 */
.el-menu--collapse {
  width: 100% !important;
}

.el-menu--collapse .el-menu-item {
  justify-content: center !important;
  padding: 0 !important;
}

.el-menu--collapse .el-menu-item .el-icon {
  margin: 0 !important;
}

.menu-item-custom {
  margin-bottom: 4px;
  border-radius: 8px;
  transition: all 0.3s;
}

.menu-item-custom:hover {
  background: rgba(59, 130, 246, 0.1) !important;
}

.menu-item-custom.is-active {
  background: rgba(59, 130, 246, 0.15) !important;
  color: #fff !important;
  border-left: 3px solid #3b82f6;
}

.system-status {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  display: flex;
  align-items: center;
  gap: 10px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.status-dot.healthy {
  background: #10b981;
  box-shadow: 0 0 8px rgba(16, 185, 129, 0.6);
}

.status-dot.warning {
  background: #f59e0b;
  box-shadow: 0 0 8px rgba(245, 158, 11, 0.6);
}

.status-dot.error {
  background: #ef4444;
  box-shadow: 0 0 8px rgba(239, 68, 68, 0.6);
}

.status-text {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.main-container {
  background: #f8fafc;
}

.layout-header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 64px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
  border-bottom: 1px solid #e2e8f0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.collapse-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #64748b;
  transition: all 0.2s;
}

.collapse-btn:hover {
  background: #f1f5f9;
  color: #334155;
}

.breadcrumb {
  display: flex;
  flex-direction: column;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.page-desc {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 2px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-time {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #f1f5f9;
  border-radius: 8px;
  font-size: 13px;
  color: #64748b;
  font-family: 'SF Mono', Monaco, monospace;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #64748b;
  transition: all 0.2s;
  background: #f8fafc;
}

.icon-btn:hover {
  background: #f1f5f9;
  color: #3b82f6;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 6px 12px 6px 6px;
  border-radius: 10px;
  transition: all 0.2s;
}

.user-info:hover {
  background: #f1f5f9;
}

.user-avatar {
  background: #3b82f6;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
}

.layout-main {
  background: #f5f7fa;
  padding: 24px;
  overflow: auto;
}

/* 动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.3s ease;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: translateY(10px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}

/* 菜单样式覆盖 */
:deep(.el-menu) {
  border: none;
}

:deep(.el-menu--collapse) {
  width: 64px;
}

:deep(.el-menu-item) {
  height: 44px;
  line-height: 44px;
  font-size: 14px;
}

:deep(.el-menu--collapse .el-menu-item) {
  padding: 0 !important;
  justify-content: center;
}

/* Badge样式 */
:deep(.el-badge__content) {
  background: linear-gradient(135deg, #ef4444 0%, #dc2626 100%);
  border: none;
}

/* 下拉菜单样式 */
:deep(.el-dropdown-menu__item) {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 响应式 */
@media (max-width: 768px) {
  .layout-aside {
    position: fixed;
    z-index: 1000;
    height: 100vh;
  }
  
  .header-time {
    display: none;
  }
  
  .user-name {
    display: none;
  }
}
</style>
