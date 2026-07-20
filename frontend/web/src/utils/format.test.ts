import { describe, it, expect } from 'vitest'
import { questionPreview, detailTime } from '../utils/format'
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
