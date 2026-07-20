import { request } from './httpClient'
import type { AgentTool } from '../types/agentTool'

export function getAll() {
  return request<AgentTool[]>('/api/agent-tools')
}

export function updateEnabled(toolId: number, enabled: boolean) {
  return request<AgentTool>(`/api/agent-tools/${toolId}/enabled`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}
