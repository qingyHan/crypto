import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Layout',
    component: () => import('@/views/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: '/dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard_Optimized.vue'),
        meta: { title: '仪表盘', icon: 'DataAnalysis' }
      },
      {
        path: '/market',
        name: 'Market',
        component: () => import('@/views/Market.vue'),
        meta: { title: '市场数据', icon: 'TrendCharts' }
      },
      {
        path: '/alerts',
        name: 'Alerts',
        component: () => import('@/views/Alerts.vue'),
        meta: { title: '预警管理', icon: 'BellFilled' }
      },
      {
        path: '/strategies',
        name: 'Strategies',
        component: () => import('@/views/Strategies.vue'),
        meta: { title: '策略配置', icon: 'Setting' }
      },
      {
        path: '/analysis',
        name: 'Analysis',
        component: () => import('@/views/Analysis.vue'),
        meta: { title: '数据分析', icon: 'PieChart' }
      },
      {
        path: '/backtest',
        name: 'Backtest',
        component: () => import('@/views/Backtest.vue'),
        meta: { title: '策略回测', icon: 'Histogram' }
      },
      {
        path: '/symbol-management',
        name: 'SymbolManagement',
        component: () => import('@/views/SymbolManagement.vue'),
        meta: { title: '交易对管理', icon: 'Coin' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
