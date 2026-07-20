import { request } from './httpClient'
import type { AgentSkill } from '../types/skill'

export function getAll() {
  return request<AgentSkill[]>('/api/skills')
}

export function create(payload: {
  name: string
  description: string
  skillContent: string
  source: string
  enabled: boolean
}) {
  return request<AgentSkill>('/api/skills', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateEnabled(skillId: number, enabled: boolean) {
  return request<AgentSkill>(`/api/skills/${skillId}/enabled`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}
