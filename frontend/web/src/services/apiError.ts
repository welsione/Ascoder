/**
 * 后端错误响应体结构，对应 GlobalExceptionHandler 的 buildResponse 输出。
 *
 * 格式：{timestamp, status, error, code, message}
 */
export interface ApiErrorBody {
  timestamp?: string
  status?: number
  error?: string
  code?: string
  message?: string
}

/**
 * API 请求失败异常。
 *
 * 解析后端结构化错误响应，message 属性已提取为后端返回的干净消息，
 * 无需调用方再从 JSON 中解析。code/status 可用于按错误类型差异化处理。
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string | undefined
  readonly timestamp: string | undefined

  constructor(status: number, body: ApiErrorBody | null, fallback: string) {
    super(body?.message || fallback)
    this.name = 'ApiError'
    this.status = status
    this.code = body?.code
    this.timestamp = body?.timestamp
  }
}

/**
 * 判断错误是否为指定错误码的 ApiError。
 *
 * 用法：if (isApiError(err, 'NOT_FOUND')) { ... }
 */
export function isApiError(err: unknown, code: string): boolean {
  return err instanceof ApiError && err.code === code
}
