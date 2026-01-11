/**
 * WebSocket 连接服务
 * 基于 SockJS 和 STOMP 协议实现与后端的实时通信
 * 
 * @author Qingyang Han
 * @since 1.0.0
 */

// WebSocket 连接状态
let stompClient = null
let isConnected = false
let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 5
const RECONNECT_DELAY = 3000

// 订阅回调存储
const subscriptions = new Map()
const callbacks = {
  onConnect: [],
  onDisconnect: [],
  onError: [],
  onAlert: [],
  onMarketStats: [],
  onPriceUpdate: []
}

/**
 * 初始化 WebSocket 连接
 * @returns {Promise<boolean>} 连接是否成功
 */
export async function initWebSocket() {
  return new Promise((resolve) => {
    // 如果已经连接，直接返回
    if (isConnected && stompClient) {
      resolve(true)
      return
    }

    try {
      // 动态导入 SockJS 和 STOMP (如果已安装)
      // 注意：需要先 npm install sockjs-client @stomp/stompjs
      import('sockjs-client').then((SockJS) => {
        import('@stomp/stompjs').then(({ Client }) => {
          const wsUrl = `${window.location.protocol === 'https:' ? 'https:' : 'http:'}//${window.location.host}/api/ws`
          
          stompClient = new Client({
            webSocketFactory: () => new SockJS.default(wsUrl),
            reconnectDelay: RECONNECT_DELAY,
            heartbeatIncoming: 4000,
            heartbeatOutgoing: 4000,
            
            onConnect: () => {
              console.log('[WebSocket] Connected successfully')
              isConnected = true
              reconnectAttempts = 0
              
              // 订阅默认主题
              subscribeToTopics()
              
              // 触发连接回调
              callbacks.onConnect.forEach(cb => cb())
              resolve(true)
            },
            
            onDisconnect: () => {
              console.log('[WebSocket] Disconnected')
              isConnected = false
              callbacks.onDisconnect.forEach(cb => cb())
            },
            
            onStompError: (frame) => {
              console.error('[WebSocket] STOMP error:', frame.headers['message'])
              callbacks.onError.forEach(cb => cb(frame))
            },
            
            onWebSocketError: (event) => {
              console.error('[WebSocket] WebSocket error:', event)
              handleReconnect()
            }
          })
          
          stompClient.activate()
        }).catch(() => {
          console.warn('[WebSocket] @stomp/stompjs not installed, using polling fallback')
          resolve(false)
        })
      }).catch(() => {
        console.warn('[WebSocket] sockjs-client not installed, using polling fallback')
        resolve(false)
      })
    } catch (error) {
      console.error('[WebSocket] Failed to initialize:', error)
      resolve(false)
    }
  })
}

/**
 * 订阅默认主题
 */
function subscribeToTopics() {
  if (!stompClient || !isConnected) return
  
  // 订阅预警主题
  const alertSub = stompClient.subscribe('/topic/alerts', (message) => {
    try {
      const alert = JSON.parse(message.body)
      callbacks.onAlert.forEach(cb => cb(alert))
    } catch (e) {
      console.error('[WebSocket] Failed to parse alert message:', e)
    }
  })
  subscriptions.set('alerts', alertSub)
  
  // 订阅市场统计主题
  const statsSub = stompClient.subscribe('/topic/market-stats', (message) => {
    try {
      const stats = JSON.parse(message.body)
      callbacks.onMarketStats.forEach(cb => cb(stats))
    } catch (e) {
      console.error('[WebSocket] Failed to parse market stats:', e)
    }
  })
  subscriptions.set('market-stats', statsSub)
  
  // 订阅系统消息
  stompClient.subscribe('/topic/system', (message) => {
    try {
      const systemMsg = JSON.parse(message.body)
      console.log('[WebSocket] System message:', systemMsg)
    } catch (e) {
      console.error('[WebSocket] Failed to parse system message:', e)
    }
  })
}

/**
 * 订阅指定交易对的价格更新
 * @param {string} symbol 交易对符号
 * @param {Function} callback 回调函数
 */
export function subscribeToPriceUpdate(symbol, callback) {
  if (!stompClient || !isConnected) {
    console.warn('[WebSocket] Not connected, cannot subscribe to price updates')
    return null
  }
  
  const subId = `price-${symbol}`
  if (subscriptions.has(subId)) {
    return subscriptions.get(subId)
  }
  
  const sub = stompClient.subscribe(`/topic/price/${symbol}`, (message) => {
    try {
      const priceData = JSON.parse(message.body)
      callback(priceData)
    } catch (e) {
      console.error(`[WebSocket] Failed to parse price update for ${symbol}:`, e)
    }
  })
  
  subscriptions.set(subId, sub)
  return sub
}

/**
 * 取消订阅指定交易对的价格更新
 * @param {string} symbol 交易对符号
 */
export function unsubscribeFromPriceUpdate(symbol) {
  const subId = `price-${symbol}`
  const sub = subscriptions.get(subId)
  if (sub) {
    sub.unsubscribe()
    subscriptions.delete(subId)
  }
}

/**
 * 处理重连
 */
function handleReconnect() {
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    console.error('[WebSocket] Max reconnect attempts reached')
    return
  }
  
  reconnectAttempts++
  console.log(`[WebSocket] Attempting to reconnect (${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...`)
  
  setTimeout(() => {
    if (!isConnected) {
      initWebSocket()
    }
  }, RECONNECT_DELAY * reconnectAttempts)
}

/**
 * 注册事件回调
 * @param {string} event 事件名称: 'connect', 'disconnect', 'error', 'alert', 'marketStats', 'priceUpdate'
 * @param {Function} callback 回调函数
 */
export function onWebSocketEvent(event, callback) {
  const eventMap = {
    'connect': 'onConnect',
    'disconnect': 'onDisconnect',
    'error': 'onError',
    'alert': 'onAlert',
    'marketStats': 'onMarketStats',
    'priceUpdate': 'onPriceUpdate'
  }
  
  const key = eventMap[event]
  if (key && callbacks[key]) {
    callbacks[key].push(callback)
  }
}

/**
 * 移除事件回调
 * @param {string} event 事件名称
 * @param {Function} callback 回调函数
 */
export function offWebSocketEvent(event, callback) {
  const eventMap = {
    'connect': 'onConnect',
    'disconnect': 'onDisconnect',
    'error': 'onError',
    'alert': 'onAlert',
    'marketStats': 'onMarketStats',
    'priceUpdate': 'onPriceUpdate'
  }
  
  const key = eventMap[event]
  if (key && callbacks[key]) {
    const index = callbacks[key].indexOf(callback)
    if (index > -1) {
      callbacks[key].splice(index, 1)
    }
  }
}

/**
 * 关闭 WebSocket 连接
 */
export function closeWebSocket() {
  if (stompClient) {
    // 取消所有订阅
    subscriptions.forEach(sub => {
      try {
        sub.unsubscribe()
      } catch (e) {
        // ignore
      }
    })
    subscriptions.clear()
    
    // 关闭连接
    stompClient.deactivate()
    stompClient = null
    isConnected = false
  }
}

/**
 * 获取连接状态
 * @returns {boolean} 是否已连接
 */
export function getConnectionStatus() {
  return isConnected
}

/**
 * 发送消息到后端
 * @param {string} destination 目标地址
 * @param {Object} body 消息体
 */
export function sendMessage(destination, body) {
  if (!stompClient || !isConnected) {
    console.warn('[WebSocket] Not connected, cannot send message')
    return false
  }
  
  try {
    stompClient.publish({
      destination: `/app${destination}`,
      body: JSON.stringify(body)
    })
    return true
  } catch (e) {
    console.error('[WebSocket] Failed to send message:', e)
    return false
  }
}

export default {
  init: initWebSocket,
  close: closeWebSocket,
  on: onWebSocketEvent,
  off: offWebSocketEvent,
  send: sendMessage,
  subscribeToPriceUpdate,
  unsubscribeFromPriceUpdate,
  isConnected: getConnectionStatus
}
