/**
 * 统一 HTTP 客户端封装。所有业务 API 服务复用此模块，避免分散的 fetch 包装。
 *
 * <p>提供默认 30s 超时和外部 AbortSignal 取消能力，避免请求挂死导致 UI 卡死。</p>
 */

export const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''

const DEFAULT_TIMEOUT_MS = 30_000

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

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { timeoutMs = DEFAULT_TIMEOUT_MS, signal, ...init } = options
  const { controller, isTimedOut, cleanup } = combineSignals(signal, timeoutMs)
  try {
    const response = await fetch(`${apiBaseUrl}${path}`, { ...init, signal: controller.signal })
    return await parseResponse<T>(response)
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      if (isTimedOut()) {
        throw new HttpTimeoutError(`请求超时(${timeoutMs}ms): ${path}`)
      }
      throw new HttpAbortError(`请求已取消: ${path}`)
    }
    throw error
  } finally {
    cleanup()
  }
}
