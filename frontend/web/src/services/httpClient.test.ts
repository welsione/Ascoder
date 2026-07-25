import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { request, HttpTimeoutError } from './httpClient'
import { ApiError } from './apiError'

describe('request error handling', () => {
  const originalFetch = globalThis.fetch

  afterEach(() => {
    globalThis.fetch = originalFetch
    vi.restoreAllMocks()
  })

  function mockFetch(status: number, body: string, contentType = 'application/json') {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      headers: new Headers({ 'content-type': contentType }),
      text: () => Promise.resolve(body),
      json: () => Promise.resolve(JSON.parse(body)),
    } as Response)
  }

  it('throws ApiError with clean message for structured error response', async () => {
    mockFetch(404, JSON.stringify({
      timestamp: '2026-01-01T00:00:00Z',
      status: 404,
      error: 'Not Found',
      code: 'NOT_FOUND',
      message: '仓库不存在: 999',
    }))

    await expect(request('/api/repositories/999')).rejects.toThrow('仓库不存在: 999')
    await expect(request('/api/repositories/999')).rejects.toBeInstanceOf(ApiError)
  })

  it('preserves error code and status in ApiError', async () => {
    mockFetch(409, JSON.stringify({
      code: 'INVALID_STATE',
      message: '项目空间正在处理中',
    }))

    try {
      await request('/api/project-spaces/1/index')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      const apiError = err as ApiError
      expect(apiError.code).toBe('INVALID_STATE')
      expect(apiError.status).toBe(409)
      expect(apiError.message).toBe('项目空间正在处理中')
    }
  })

  it('falls back to raw text for non-JSON error response', async () => {
    mockFetch(502, 'Bad Gateway', 'text/html')

    await expect(request('/api/test')).rejects.toThrow('Bad Gateway')
  })

  it('wraps network errors with friendly message', async () => {
    globalThis.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))

    await expect(request('/api/test')).rejects.toThrow('网络连接失败，请检查网络后重试')
  })

  it('throws HttpTimeoutError on timeout', async () => {
    globalThis.fetch = vi.fn().mockImplementation((_url, init) => {
      return new Promise((_resolve, reject) => {
        const signal = (init as RequestInit)?.signal
        if (signal) {
          signal.addEventListener('abort', () => {
            const err = new DOMException('aborted', 'AbortError')
            reject(err)
          })
        }
      })
    })

    await expect(request('/api/test', { timeoutMs: 50 })).rejects.toBeInstanceOf(HttpTimeoutError)
  })
})
