import request from '@/utils/request'

/**
 * 获取预警列表
 */
export const getAlerts = (params) => {
  return request({
    url: '/alerts',
    method: 'get',
    params
  })
}

/**
 * 获取预警详情
 */
export const getAlertDetail = (alertId) => {
  return request({
    url: `/alerts/${alertId}`,
    method: 'get'
  })
}

/**
 * 标记预警已读
 */
export const markAlertAsRead = (alertId) => {
  return request({
    url: `/alerts/${alertId}/read`,
    method: 'put'
  })
}

/**
 * 批量标记预警已读
 */
export const batchMarkAsRead = (alertIds) => {
  return request({
    url: '/alerts/batch-read',
    method: 'put',
    data: alertIds  // 后端期望直接接收 Long[] 数组
  })
}

/**
 * 获取未读预警数量
 */
export const getUnreadCount = () => {
  return request({
    url: '/alerts/unread/count',
    method: 'get'
  })
}

/**
 * 获取预警统计数据
 */
export const getAlertStats = () => {
  return request({
    url: '/alerts/statistics',
    method: 'get'
  })
}

/**
 * 删除单个预警
 */
export const deleteAlert = (alertId) => {
  return request({
    url: `/alerts/${alertId}`,
    method: 'delete'
  })
}

/**
 * 批量删除预警
 */
export const batchDeleteAlerts = (alertIds) => {
  return request({
    url: '/alerts/batch',
    method: 'delete',
    data: alertIds
  })
}

/**
 * 删除过期预警
 */
export const deleteExpiredAlerts = (days = 30) => {
  return request({
    url: '/alerts/expired',
    method: 'delete',
    params: { days }
  })
}
