import request from '@/utils/request'

/**
 * 获取策略列表
 */
export const getStrategies = (params) => {
  return request({
    url: '/strategies',
    method: 'get',
    params
  })
}

/**
 * 获取策略详情
 */
export const getStrategyDetail = (strategyId) => {
  return request({
    url: `/strategies/${strategyId}`,
    method: 'get'
  })
}

/**
 * 创建策略
 */
export const createStrategy = (data) => {
  return request({
    url: '/strategies',
    method: 'post',
    data
  })
}

/**
 * 更新策略
 */
export const updateStrategy = (strategyId, data) => {
  return request({
    url: `/strategies/${strategyId}`,
    method: 'put',
    data
  })
}

/**
 * 删除策略
 */
export const deleteStrategy = (strategyId) => {
  return request({
    url: `/strategies/${strategyId}`,
    method: 'delete'
  })
}

/**
 * 启用/禁用策略
 * 注意：后端期望 enabled 作为 URL 查询参数
 */
export const toggleStrategy = (strategyId, enabled) => {
  return request({
    url: `/strategies/${strategyId}/toggle`,
    method: 'put',
    params: { enabled }  // 使用 params 而非 data，作为 URL 查询参数
  })
}
