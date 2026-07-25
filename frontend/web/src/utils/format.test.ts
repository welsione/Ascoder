import { describe, it, expect } from 'vitest'
import { questionPreview, detailTime, formatTime } from '../utils/format'
import type { QuestionRecord } from '../types/question'

describe('questionPreview', () => {
  it('returns short question unchanged', () => {
    expect(questionPreview('hello')).toBe('hello')
  })

  it('truncates long question with ellipsis', () => {
    const long = 'a'.repeat(100)
    expect(questionPreview(long)).toBe('a'.repeat(78) + '...')
  })

  it('keeps question at exactly 78 chars unchanged', () => {
    const exact = 'a'.repeat(78)
    expect(questionPreview(exact)).toBe(exact)
  })
})

describe('detailTime', () => {
  const base = { id: 1, status: 'PENDING', text: 'q', createdAt: '2025-01-01' } as QuestionRecord

  it('prefers completedAt if present', () => {
    expect(detailTime({ ...base, completedAt: 'c', startedAt: 's', createdAt: 'cr' } as QuestionRecord)).toBe('c')
  })

  it('falls back to startedAt', () => {
    expect(detailTime({ ...base, startedAt: 's', createdAt: 'cr' } as QuestionRecord)).toBe('s')
  })

  it('falls back to createdAt', () => {
    expect(detailTime(base)).toBe('2025-01-01')
  })
})

describe('formatTime', () => {
  it('formats ISO string to YYYY-MM-DD HH:mm:ss', () => {
    expect(formatTime('2025-07-15T14:30:45.123Z')).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
  })

  it('returns fallback for null/undefined/empty', () => {
    expect(formatTime(null)).toBe('--')
    expect(formatTime(undefined)).toBe('--')
    expect(formatTime('')).toBe('--')
  })

  it('supports custom fallback', () => {
    expect(formatTime(null, '未同步')).toBe('未同步')
    expect(formatTime(undefined, '未索引')).toBe('未索引')
  })

  it('returns fallback for invalid date string', () => {
    expect(formatTime('not-a-date')).toBe('--')
  })

  it('pads single-digit values with leading zeros', () => {
    // 1月1日 01:02:03 UTC
    const result = formatTime('2025-01-01T01:02:03.000Z')
    expect(result).toMatch(/2025-0[1-2]-0[1-2] 0[0-9]:02:03/)
  })
})
