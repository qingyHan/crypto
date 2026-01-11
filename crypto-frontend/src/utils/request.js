import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'

const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

// 错误消息去重，避免重复提示
let lastErrorTime = 0
let lastErrorMsg = ''
const showErrorOnce = (msg, type = 'error') => {
  const now = Date.now()
  if (msg === lastErrorMsg && now - lastErrorTime < 3000) {
    return // 3秒内相同错误不重复提示
  }
  lastErrorTime = now
  lastErrorMsg = msg
  
  if (type === 'warning') {
    ElMessage.warning(msg)
  } else {
    ElMessage.error(msg)
  }
}

const request = axios.create({
  baseURL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

// 请求重试逻辑已集成到响应拦截器中

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    
    // 标记是否已重试
    if (!config._retryCount) {
      config._retryCount = 0
    }
    
    return config
  },
  (error) => {
    console.error('Request error:', error)
    return Promise.reject(error)
  }
)

// 响应拦截器 - 增强兼容性
request.interceptors.response.use(
  (response) => {
    // 处理多种响应格式
    const responseData = response.data
    
    // 情况1: 标准ApiResponse格式 {code: 200, data: {...}, message: "..."}
    if (responseData && typeof responseData === 'object' && 'code' in responseData) {
      const { code, data, message } = responseData
      if (code === 200 || code === 0) {
        return data !== undefined ? data : responseData
      } else {
        // 非成功状态码，但不抛出错误，返回空数据让调用方处理
        console.warn(`API returned code ${code}: ${message || 'Unknown error'}`)
        return data !== undefined ? data : null
      }
    }
    
    // 情况2: 直接返回数据（数组或对象）
    if (Array.isArray(responseData) || (responseData && typeof responseData === 'object')) {
      return responseData
    }
    
    // 情况3: 其他格式，直接返回
    return responseData
  },
  async (error) => {
    const config = error.config || {}
    
    // 重试逻辑
    if (!config._retryCount) {
      config._retryCount = 0
    }
    
    const maxRetries = config.retry !== undefined ? config.retry : 2
    const shouldRetry = 
      config._retryCount < maxRetries &&
      (!error.response || error.response.status >= 500 || error.code === 'ECONNABORTED' || error.code === 'ECONNREFUSED')
    
    if (shouldRetry) {
      config._retryCount += 1
      await new Promise(resolve => setTimeout(resolve, 1000 * config._retryCount))
      return request(config)
    }
    
    // 静默处理常见错误，避免过度提示
    if (error.response) {
      const { status, data } = error.response
      const serverMsg = data?.message || data?.msg || ''

      if (status === 401) {
        // 系统未启用登录验证，静默处理
        console.warn('401 Unauthorized - 系统未启用登录验证')
        return Promise.reject(error)
      } else if (status === 403) {
        showErrorOnce('没有权限执行此操作')
      } else if (status === 404) {
        // 404不显示错误，很多接口可能不存在
        console.warn('Resource not found:', error.config?.url)
      } else if (status === 429) {
        showErrorOnce('请求过于频繁，请稍后再试', 'warning')
      } else if (status >= 500) {
        // 只在首次出现时提示
        showErrorOnce('服务器暂时不可用，请稍后重试')
      } else if (status >= 400 && serverMsg) {
        // 其他4xx错误，只在有明确消息时提示
        showErrorOnce(serverMsg, 'warning')
      }
    } else if (error.code === 'ECONNABORTED') {
      // 超时错误，减少提示频率
      const timeoutKey = 'timeout_error'
      const lastTimeout = sessionStorage.getItem(timeoutKey)
      const now = Date.now()
      if (!lastTimeout || now - parseInt(lastTimeout) > 10000) {
        sessionStorage.setItem(timeoutKey, now.toString())
        showErrorOnce('请求超时，请检查网络连接', 'warning')
      }
    } else if (error.request) {
      // 网络错误 - 只在首次出现时提示
      const networkKey = 'network_error'
      const lastNetwork = sessionStorage.getItem(networkKey)
      const now = Date.now()
      if (!lastNetwork || now - parseInt(lastNetwork) > 15000) {
        sessionStorage.setItem(networkKey, now.toString())
        ElNotification({
          title: '网络连接失败',
          message: '无法连接到服务器，请确保后端服务已启动',
          type: 'warning',
          duration: 5000
        })
      }
    }

    return Promise.reject(error)
  }
)

export default request
