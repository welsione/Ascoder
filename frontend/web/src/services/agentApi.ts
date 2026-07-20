import { request } from './httpClient'
import type {
  AgentConfig,
  AgentRunRecord,
  AgentRuntimeView,
  CreateAgentConfigRequest,
  UpdateAgentConfigRequest,
  TestRenderRequest,
  TestRenderResponse,
  AgentPage,
} from '../types/agent'

export function getAll() {
  return request<AgentConfig[]>('/api/agents')
}

export function getById(id: number) {
  return request<AgentConfig>(`/api/agents/${id}`)
}

export function create(payload: CreateAgentConfigRequest) {
  return request<AgentConfig>('/api/agents', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function update(id: number, payload: UpdateAgentConfigRequest) {
  return request<AgentConfig>(`/api/agents/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function remove(id: number) {
  return request<void>(`/api/agents/${id}`, { method: 'DELETE' })
}

export function updateEnabled(id: number, enabled: boolean) {
  return request<AgentConfig>(`/api/agents/${id}/enabled`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}

export function testRender(id: number, sampleContext: Record<string, unknown> | null) {
  return request<TestRenderResponse>(`/api/agents/${id}/test-render`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ sampleContext }),
  })
}

export function listStatuses() {
  return request<AgentRuntimeView[]>('/api/agents/status')
}

export function listRuns(agentId: string, page = 0, size = 10) {
  return request<AgentPage>(`/api/agents/${agentId}/runs?page=${page}&size=${size}`)
}

export function getRun(agentId: string, runId: number) {
  return request<AgentRunRecord>(`/api/agents/${agentId}/runs/${runId}`)
}

export function subscribeStatus(
  agentId: string,
  onStatus: (data: { agentId: string; status: string; questionId?: number | null; runRecordId?: number }) => void,
): EventSource {
  const es = new EventSource(`/api/agents/${agentId}/status`)
  es.addEventListener('status', (event: MessageEvent) => {
    try {
      onStatus(JSON.parse(event.data))
    } catch {
      // ignore malformed event
    }
  })
  return es
}
