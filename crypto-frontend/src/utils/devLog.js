/**
 * 开发环境日志工具
 * 生产环境自动禁用所有日志输出
 */

const isDev = import.meta.env.DEV

/**
 * 开发环境日志输出
 * @param {...any} args - 日志参数
 */
export const devLog = (...args) => {
  if (isDev) {
    console.log('[DEV]', ...args)
  }
}

/**
 * 开发环境警告输出
 * @param {...any} args - 警告参数
 */
export const devWarn = (...args) => {
  if (isDev) {
    console.warn('[DEV]', ...args)
  }
}

/**
 * 开发环境错误输出
 * @param {...any} args - 错误参数
 */
export const devError = (...args) => {
  if (isDev) {
    console.error('[DEV]', ...args)
  }
}

export default {
  log: devLog,
  warn: devWarn,
  error: devError
}
