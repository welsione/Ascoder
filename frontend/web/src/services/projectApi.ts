import { request } from './httpClient'
import type { Project, ProjectRepositoryMember } from '../types/project'

export function getAll() {
  return request<Project[]>('/api/projects')
}

export function create(payload: { name: string; description?: string | null }) {
  return request<Project>('/api/projects', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function getMembers(projectId: number) {
  return request<ProjectRepositoryMember[]>(`/api/projects/${projectId}/repositories`)
}

export function addRepository(
  projectId: number,
  payload: {
    repositoryId: number
    alias?: string | null
    role?: string | null
    primaryRepository?: boolean
    sortOrder?: number | null
  }
) {
  return request<ProjectRepositoryMember>(`/api/projects/${projectId}/repositories`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function removeRepository(projectId: number, memberId: number) {
  return request<void>(`/api/projects/${projectId}/repositories/${memberId}`, {
    method: 'DELETE',
  })
}
