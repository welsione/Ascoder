import { describe, it, expect } from 'vitest'
import { ApiError, isApiError } from './apiError'

describe('ApiError', () => {
  it('extracts message from structured error body', () => {
    const error = new ApiError(404, {
      timestamp: '2026-01-01T00:00:00Z',
      status: 404,
      error: 'Not Found',
      code: 'NOT_FOUND',
      message: '仓库不存在: 999',
    }, 'fallback')

    expect(error.message).toBe('仓库不存在: 999')
    expect(error.code).toBe('NOT_FOUND')
    expect(error.status).toBe(404)
    expect(error.timestamp).toBe('2026-01-01T00:00:00Z')
  })

  it('falls back to provided text when body is null', () => {
    const error = new ApiError(502, null, 'Bad Gateway')

    expect(error.message).toBe('Bad Gateway')
    expect(error.code).toBeUndefined()
  })

  it('falls back to provided text when body has no message', () => {
    const error = new ApiError(500, { status: 500, code: 'INTERNAL_ERROR' }, 'fallback')

    expect(error.message).toBe('fallback')
    expect(error.code).toBe('INTERNAL_ERROR')
  })

  it('is an instance of Error', () => {
    const error = new ApiError(400, { message: 'bad request' }, 'fallback')

    expect(error).toBeInstanceOf(Error)
    expect(error.name).toBe('ApiError')
  })
})

describe('isApiError', () => {
  it('returns true for matching code', () => {
    const error = new ApiError(404, { code: 'NOT_FOUND', message: 'not found' }, 'fallback')

    expect(isApiError(error, 'NOT_FOUND')).toBe(true)
  })

  it('returns false for non-matching code', () => {
    const error = new ApiError(404, { code: 'NOT_FOUND', message: 'not found' }, 'fallback')

    expect(isApiError(error, 'INVALID_STATE')).toBe(false)
  })

  it('returns false for non-ApiError', () => {
    expect(isApiError(new Error('plain'), 'NOT_FOUND')).toBe(false)
    expect(isApiError('string', 'NOT_FOUND')).toBe(false)
    expect(isApiError(null, 'NOT_FOUND')).toBe(false)
  })
})
