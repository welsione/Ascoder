import { request } from './httpClient'
import type {
  CleanupLegacyInsightsResult,
  CleanupLegacyRawEventsResult,
  ImportHistoryRawEventsResult,
  LearningInsight,
  LearningInsightVerification,
  LearningInsightStatus,
  LearningAgentRunRecord,
  LearningKnowledgeItem,
  LearningKnowledgeStatus,
  LearningRawEvent,
  RefineLearningInsightResult,
  SaveLearningInsightPayload,
  SaveLearningKnowledgeItemPayload,
  SelfLearningAgentRun,
  SelfLearningSettings,
  SelfLearningSummary,
} from '../types/selfLearning'

function base(projectSpaceId: number) {
  return `/api/project-spaces/${projectSpaceId}/self-learning`
}

export function getSummary(projectSpaceId: number) {
  return request<SelfLearningSummary>(`${base(projectSpaceId)}/summary`)
}

export function updateSettings(projectSpaceId: number, payload: Partial<SelfLearningSettings>) {
  return request<SelfLearningSettings>(`${base(projectSpaceId)}/settings`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function listRawEvents(projectSpaceId: number) {
  return request<LearningRawEvent[]>(`${base(projectSpaceId)}/raw-events`)
}

export function importHistoryRawEvents(projectSpaceId: number) {
  return request<ImportHistoryRawEventsResult>(`${base(projectSpaceId)}/raw-events/import-history`, {
    method: 'POST',
  })
}

export function cleanupLegacyRawEvents(projectSpaceId: number) {
  return request<CleanupLegacyRawEventsResult>(`${base(projectSpaceId)}/raw-events/cleanup-legacy`, {
    method: 'POST',
  })
}

export function cleanupLegacyInsights(projectSpaceId: number) {
  return request<CleanupLegacyInsightsResult>(`${base(projectSpaceId)}/insights/cleanup-legacy`, {
    method: 'POST',
  })
}

export function runSelfLearningAgent(projectSpaceId: number, limit = 12) {
  return request<SelfLearningAgentRun>(`${base(projectSpaceId)}/agent-runs?limit=${encodeURIComponent(limit)}`, {
    method: 'POST',
  })
}

export function listAgentRuns(projectSpaceId: number) {
  return request<LearningAgentRunRecord[]>(`${base(projectSpaceId)}/agent-runs`)
}

export function listInsights(projectSpaceId: number, status?: LearningInsightStatus | '') {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<LearningInsight[]>(`${base(projectSpaceId)}/insights${query}`)
}

export function createInsight(projectSpaceId: number, payload: SaveLearningInsightPayload) {
  return request<LearningInsight>(`${base(projectSpaceId)}/insights`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateInsight(projectSpaceId: number, insightId: number, payload: SaveLearningInsightPayload) {
  return request<LearningInsight>(`${base(projectSpaceId)}/insights/${insightId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function approveInsight(projectSpaceId: number, insightId: number, reviewComment = '') {
  return request<LearningInsight>(`${base(projectSpaceId)}/insights/${insightId}/approve`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reviewComment }),
  })
}

export function rejectInsight(projectSpaceId: number, insightId: number, reviewComment = '') {
  return request<LearningInsight>(`${base(projectSpaceId)}/insights/${insightId}/reject`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reviewComment }),
  })
}

export function verifyInsight(projectSpaceId: number, insightId: number) {
  return request<LearningInsightVerification>(`${base(projectSpaceId)}/insights/${insightId}/verify`, {
    method: 'POST',
  })
}

export function refineInsight(projectSpaceId: number, insightId: number, instruction: string) {
  return request<RefineLearningInsightResult>(`${base(projectSpaceId)}/insights/${insightId}/refine`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ instruction }),
  })
}

export function listKnowledgeItems(projectSpaceId: number, status?: LearningKnowledgeStatus | '') {
  const query = status ? `?status=${encodeURIComponent(status)}` : ''
  return request<LearningKnowledgeItem[]>(`${base(projectSpaceId)}/knowledge-items${query}`)
}

export function createKnowledgeItem(projectSpaceId: number, payload: SaveLearningKnowledgeItemPayload) {
  return request<LearningKnowledgeItem>(`${base(projectSpaceId)}/knowledge-items`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateKnowledgeItem(projectSpaceId: number, itemId: number, payload: SaveLearningKnowledgeItemPayload) {
  return request<LearningKnowledgeItem>(`${base(projectSpaceId)}/knowledge-items/${itemId}`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function deleteKnowledgeItem(projectSpaceId: number, itemId: number) {
  return request<void>(`${base(projectSpaceId)}/knowledge-items/${itemId}`, { method: 'DELETE' })
}

export function archiveKnowledgeItem(projectSpaceId: number, itemId: number) {
  return request<LearningKnowledgeItem>(`${base(projectSpaceId)}/knowledge-items/${itemId}/archive`, { method: 'POST' })
}

export function markKnowledgeItemStale(projectSpaceId: number, itemId: number, reviewComment = '') {
  return request<LearningKnowledgeItem>(`${base(projectSpaceId)}/knowledge-items/${itemId}/mark-stale`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reviewComment }),
  })
}
