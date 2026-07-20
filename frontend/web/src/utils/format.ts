import type { QuestionRecord } from '../types/question'

export function questionPreview(question: string) {
  return question.length > 78 ? `${question.slice(0, 78)}...` : question
}

export function detailTime(record: QuestionRecord) {
  return record.completedAt ?? record.startedAt ?? record.createdAt
}
