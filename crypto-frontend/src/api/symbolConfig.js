import request from '@/utils/request'

/**
 * 交易对配置管理API
 */

/**
 * 获取所有启用的交易对
 */
export const getActiveSymbols = () => {
  return request({
    url: '/symbol-config/active',
    method: 'get'
  })
}

/**
 * 分页查询交易对配置
 */
export const querySymbolConfigs = (params) => {
  return request({
    url: '/symbol-config/page',
    method: 'get',
    params
  })
}

/**
 * 根据ID获取交易对配置
 */
export const getSymbolConfigById = (id) => {
  return request({
    url: `/symbol-config/${id}`,
    method: 'get'
  })
}

/**
 * 新增交易对配置
 */
export const addSymbolConfig = (data) => {
  return request({
    url: '/symbol-config',
    method: 'post',
    data
  })
}

/**
 * 更新交易对配置
 */
export const updateSymbolConfig = (id, data) => {
  return request({
    url: `/symbol-config/${id}`,
    method: 'put',
    data
  })
}

/**
 * 删除交易对配置
 */
export const deleteSymbolConfig = (id) => {
  return request({
    url: `/symbol-config/${id}`,
    method: 'delete'
  })
}

/**
 * 启用/禁用交易对
 */
export const toggleSymbolStatus = (id, isActive) => {
  return request({
    url: `/symbol-config/${id}/toggle`,
    method: 'patch',
    params: { isActive }
  })
}

/**
 * 批量删除交易对
 */
export const batchDeleteSymbols = (ids) => {
  return request({
    url: '/symbol-config/batch',
    method: 'delete',
    data: ids
  })
}

/**
 * 获取所有可采集的交易对
 */
export const getCollectableSymbols = () => {
  return request({
    url: '/symbol-config/collectable',
    method: 'get'
  })
}
