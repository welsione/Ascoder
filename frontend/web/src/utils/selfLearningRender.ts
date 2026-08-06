import type {
  LearningAgentRunStatus,
  LearningInsightStatus,
  LearningInsightVerificationStatus,
  LearningKnowledgeStatus,
  LearningKnowledgeType,
} from '../types/selfLearning'

export type JsonObject = Record<string, unknown>

export function typeLabel(value: LearningKnowledgeType, options: Array<{ label: string; value: LearningKnowledgeType }>) {
  return options.find((item) => item.value === value)?.label ?? value
}

export function insightStatusLabel(value: LearningInsightStatus, options?: Array<{ label: string; value: LearningInsightStatus }>) {
  return options?.find((item) => item.value === value)?.label ?? value
}

export function knowledgeStatusLabel(value: LearningKnowledgeStatus, options?: Array<{ label: string; value: LearningKnowledgeStatus }>) {
  return options?.find((item) => item.value === value)?.label ?? value
}

export function insightTagType(value: LearningInsightStatus): 'warning' | 'success' | 'danger' | 'info' {
  if (value === 'PENDING_REVIEW') return 'warning'
  if (value === 'APPROVED' || value === 'MERGED') return 'success'
  if (value === 'REJECTED') return 'danger'
  return 'info'
}

export function knowledgeTagType(value: LearningKnowledgeStatus): 'warning' | 'success' | 'danger' | 'info' {
  if (value === 'ACTIVE' || value === 'VERIFIED') return 'success'
  if (value === 'STALE') return 'warning'
  if (value === 'REJECTED' || value === 'NEGATIVE') return 'danger'
  return 'info'
}

export function agentRunStatusLabel(value: LearningAgentRunStatus | null): string {
  if (value === 'QUEUED') return '排队中'
  if (value === 'RUNNING') return '整理中'
  if (value === 'SUCCEEDED') return '已完成'
  if (value === 'PARTIAL_FAILED') return '部分失败'
  if (value === 'SKIPPED') return '已跳过'
  if (value === 'FAILED') return '失败'
  return '未记录'
}

export function agentRunStatusType(value: LearningAgentRunStatus | null): 'warning' | 'success' | 'danger' | 'info' {
  if (value === 'RUNNING' || value === 'QUEUED') return 'warning'
  if (value === 'SUCCEEDED') return 'success'
  if (value === 'FAILED' || value === 'PARTIAL_FAILED') return 'danger'
  return 'info'
}

export function verificationStatusLabel(value: LearningInsightVerificationStatus | null | undefined): string {
  if (value === 'VERIFIED') return '代码支持'
  if (value === 'NEEDS_CHANGES') return '建议修改'
  if (value === 'INSUFFICIENT_EVIDENCE') return '证据不足'
  if (value === 'CONTRADICTED') return '存在冲突'
  return value || '未复核'
}

export function verificationStatusType(value: LearningInsightVerificationStatus | null | undefined): 'success' | 'warning' | 'danger' | 'info' {
  if (value === 'VERIFIED') return 'success'
  if (value === 'NEEDS_CHANGES' || value === 'INSUFFICIENT_EVIDENCE') return 'warning'
  if (value === 'CONTRADICTED') return 'danger'
  return 'info'
}

export function compactText(value: string | null | undefined, fallback = '暂无内容'): string {
  if (!value || !value.trim()) return fallback
  return value
}

export function prettyJson(value: string | null | undefined): string {
  if (!value || !value.trim()) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

export function parseJsonValue(value: string | null | undefined): unknown {
  if (!value || !value.trim()) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

export function readableJsonValue(value: unknown): string {
  if (value === null || value === undefined || value === '') return '未提供'
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value, null, 2)
}

export function asObject(value: unknown): JsonObject | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as JsonObject) : null
}

export function arrayObjects(value: string | null | undefined): JsonObject[] {
  const parsed = parseJsonValue(value)
  if (Array.isArray(parsed)) {
    return parsed.map(asObject).filter((item): item is JsonObject => Boolean(item))
  }
  const object = asObject(parsed)
  return object ? [object] : []
}

export function glossaryItems(value: string | null | undefined) {
  return arrayObjects(value).map((item, index) => ({
    key: `glossary-${index}`,
    term: readableJsonValue(item.term ?? item.name ?? item.businessTerm ?? item.word),
    meaning: readableJsonValue(item.meaning ?? item.definition ?? item.description ?? item.value),
    codeSymbol: readableJsonValue(item.codeSymbol ?? item.symbol ?? item.code ?? item.path),
    source: readableJsonValue(item.source ?? item.evidence),
  }))
}

export function symbolItems(value: string | null | undefined) {
  const parsed = parseJsonValue(value)
  if (Array.isArray(parsed)) {
    return parsed.map((item, index) => {
      const object = asObject(item)
      return {
        key: `symbol-${index}`,
        symbol: object ? readableJsonValue(object.symbol ?? object.name ?? object.codeSymbol) : readableJsonValue(item),
        kind: object ? readableJsonValue(object.kind ?? object.type) : '',
        file: object ? readableJsonValue(object.file ?? object.path) : '',
      }
    })
  }
  const object = asObject(parsed)
  if (object) {
    return Object.entries(object).map(([key, item]) => ({
      key,
      symbol: key,
      kind: '',
      file: readableJsonValue(item),
    }))
  }
  return []
}

export function evidenceItems(value: string | null | undefined) {
  const parsed = parseJsonValue(value)
  const list = Array.isArray(parsed)
    ? parsed
    : asObject(parsed)
      ? Object.entries(parsed as JsonObject).map(([key, item]) => ({ key, value: item }))
      : []
  return list.map((item, index) => {
    const object = asObject(item)
    return {
      key: `evidence-${index}`,
      title: readableJsonValue(object?.title ?? object?.file ?? object?.path ?? object?.symbol ?? object?.key ?? `证据 ${index + 1}`),
      summary: readableJsonValue(object?.summary ?? object?.description ?? object?.value ?? object?.reason ?? item),
      meta: [
        object?.questionId ? `Question #${readableJsonValue(object.questionId)}` : '',
        object?.rawEventId ? `Raw #${readableJsonValue(object.rawEventId)}` : '',
        object?.tool ? `工具 ${readableJsonValue(object.tool)}` : '',
      ].filter(Boolean),
    }
  })
}

export function gitItems(value: string | null | undefined) {
  const parsed = parseJsonValue(value)
  const list = Array.isArray(parsed) ? parsed : asObject(parsed) ? [parsed] : []
  return list.map((item, index) => {
    const object = asObject(item)
    return {
      key: `git-${index}`,
      commit: readableJsonValue(object?.commitSha ?? object?.sha ?? object?.commit ?? object?.id ?? `Git 线索 ${index + 1}`),
      message: readableJsonValue(object?.commitMessage ?? object?.message ?? object?.summary ?? item),
      author: readableJsonValue(object?.author ?? object?.authorName),
      branch: readableJsonValue(object?.branch ?? object?.branchName),
    }
  })
}

export function jsonFieldItems(value: string | null | undefined) {
  if (!value || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) {
      return parsed.map((item, index) => ({
        key: `#${index + 1}`,
        value: typeof item === 'string' ? item : JSON.stringify(item, null, 2),
      }))
    }
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed).map(([key, item]) => ({
        key,
        value: typeof item === 'string' ? item : JSON.stringify(item, null, 2),
      }))
    }
  } catch {
    return []
  }
  return []
}

/**
 * 从 Agent 运行失败的 JSON 详情中提取错误消息摘要列表。
 *
 * 详情结构为数组：[{"rawEventIds":[...],"errorMessage":"...","failedAt":"..."}]，
 * 展示时只取 errorMessage，避免原始 JSON 堆栈铺满界面。
 */
export function failureSummaries(failureDetailsJson: string | null | undefined): string[] {
  const parsed = parseJsonValue(failureDetailsJson)
  if (!Array.isArray(parsed)) return []
  return parsed
    .map((item) => {
      const object = asObject(item)
      const message = object?.errorMessage ?? object?.message ?? object?.reason
      return typeof message === 'string' ? message.trim() : ''
    })
    .filter((message) => message.length > 0)
}
