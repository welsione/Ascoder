import type { QuestionRecord } from '../types/question'

export function questionPreview(question: string) {
  return question.length > 78 ? `${question.slice(0, 78)}...` : question
}

export function detailTime(record: QuestionRecord) {
  return record.completedAt ?? record.startedAt ?? record.createdAt
}

/**
 * 将 ISO 时间字符串格式化为 YYYY-MM-DD HH:mm:ss
 * @param value ISO 时间字符串，空值返回 fallback
 * @param fallback 空值时的回退文本，默认 '--'
 */
export function formatTime(value?: string | null, fallback = '--'): string {
  if (!value) return fallback
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return fallback
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
