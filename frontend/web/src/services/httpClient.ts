/**
 * 统一 HTTP 客户端封装。所有业务 API 服务复用此模块，避免分散的 fetch 包装。
 *
 * <p>提供默认 30s 超时和外部 AbortSignal 取消能力，避免请求挂死导致 UI 卡死。</p>
 * <p>自动注入 Authorization 头，并在 401 时用 Refresh Token 刷新后重放请求。</p>
 */

import { useAuthStore } from '../stores/auth'

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

const DEFAULT_TIMEOUT_MS = 30_000
const AUTH_HEADER = 'Authorization'
const BEARER_PREFIX = 'Bearer '

export class HttpTimeoutError extends Error {
  constructor(message = '请求超时') {
    super(message)
    this.name = 'HttpTimeoutError'
  }
}

export class HttpAbortError extends Error {
  constructor(message = '请求已取消') {
    super(message)
    this.name = 'HttpAbortError'
  }
}

export interface RequestOptions extends RequestInit {
  /** 超时毫秒数，默认 30000；传 0 或负数表示不超时。 */
  timeoutMs?: number
  /** 外部 AbortSignal，用于取消请求。 */
  signal?: AbortSignal
}

async function parseResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    const detail = await response.text()
    throw new Error(detail || `HTTP ${response.status}`)
  }
  if (response.status === 204) {
    return undefined as T
  }
  const contentType = response.headers.get('content-type') ?? ''
  if (contentType.includes('application/json')) {
    return (await response.json()) as T
  }
  return (await response.text()) as T
}

/**
 * 将外部 signal 与超时合并为单一 AbortController：任一触发即中断 fetch。
 * 返回 controller 和清理函数；调用方在 finally 中执行 cleanup 释放定时器/监听器。
 */
function combineSignals(externalSignal: AbortSignal | undefined, timeoutMs: number) {
  const controller = new AbortController()
  let timeoutHandle: ReturnType<typeof setTimeout> | null = null
  let timedOut = false

  if (externalSignal) {
    if (externalSignal.aborted) {
      controller.abort()
    } else {
      externalSignal.addEventListener('abort', onExternalAbort, { once: true })
    }
  }
  if (timeoutMs > 0) {
    timeoutHandle = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, timeoutMs)
  }

  function onExternalAbort() {
    controller.abort()
  }

  return {
    controller,
    isTimedOut: () => timedOut,
    cleanup() {
      if (timeoutHandle !== null) {
        clearTimeout(timeoutHandle)
      }
      externalSignal?.removeEventListener('abort', onExternalAbort)
    },
  }
}

// 刷新锁：防止并发请求同时触发多次刷新
let refreshPromise: Promise<string> | null = null

/**
 * 获取有效 Access Token 的 Authorization 头值。
 */
function getAuthHeader(): string | null {
  const auth = useAuthStore()
  if (!auth.accessToken) return null
  return `${BEARER_PREFIX}${auth.accessToken}`
}

/**
 * 用 Refresh Token 刷新 Access Token（带并发锁，同一时刻只发一次刷新请求）。
 */
async function refreshAccessToken(): Promise<string | null> {
  const auth = useAuthStore()
  if (!auth.refreshToken) return null
  if (!refreshPromise) {
    refreshPromise = auth.refresh().finally(() => {
      refreshPromise = null
    })
  }
  try {
    return await refreshPromise
  } catch {
    return null
  }
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, signal, ...init } = options
  const { controller, isTimedOut, cleanup } = combineSignals(signal, timeoutMs)

  // 注入 Authorization 头
  const headers = new Headers(init.headers)
  const authHeader = getAuthHeader()
  if (authHeader) {
    headers.set(AUTH_HEADER, authHeader)
  }

  try {
    const response = await fetch(`${apiBaseUrl}${path}`, { ...init, headers, signal: controller.signal })

    // 401 且有 refreshToken -> 尝试刷新后重放
    if (response.status === 401 && useAuthStore().refreshToken) {
      const newToken = await refreshAccessToken()
      if (newToken) {
        headers.set(AUTH_HEADER, `${BEARER_PREFIX}${newToken}`)
        const retryResponse = await fetch(`${apiBaseUrl}${path}`, { ...init, headers, signal: controller.signal })
        return await parseResponse<T>(retryResponse)
      }
      // 刷新失败 -> 清除 Token
      useAuthStore().clearTokens()
      throw new Error('登录已过期，请重新登录')
    }

    return await parseResponse<T>(response)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      if (isTimedOut()) {
        throw new HttpTimeoutError(`请求超时(${timeoutMs}ms): ${path}`)
      }
      throw new HttpAbortError(`请求已取消: ${path}`)
    }
    // 网络错误（服务器不可达、DNS 失败等），fetch 抛出 TypeError，消息对用户不友好
    if (error instanceof TypeError) {
      throw new Error('网络连接失败，请检查网络后重试')
    }
    throw error
  } finally {
    cleanup()
  }
}
