import { request } from './httpClient'
import type { CodeRepository, RepositoryBranch } from '../types/repository'

export function getAll() {
  return request<CodeRepository[]>('/api/repositories')
}

export function get(id: number) {
  return request<CodeRepository>(`/api/repositories/${id}`)
}

export function create(payload: {
  name: string
  localPath?: string
  remoteUrl?: string
  defaultBranch?: string
  authUsername?: string
  authPassword?: string
}) {
  return request<CodeRepository>('/api/repositories', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateCredentials(
  repositoryId: number,
  payload: { authUsername?: string; authPassword?: string }
) {
  return request<CodeRepository>(`/api/repositories/${repositoryId}/credentials`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function triggerIndex(repositoryId: number) {
  return request<CodeRepository>(`/api/repositories/${repositoryId}/index`, {
    method: 'POST',
  })
}

export function fetch(repositoryId: number) {
  return request<CodeRepository>(`/api/repositories/${repositoryId}/fetch`, {
    method: 'POST',
  })
}

export function pull(repositoryId: number) {
  return request<CodeRepository>(`/api/repositories/${repositoryId}/pull`, {
    method: 'POST',
  })
}

export function getIndexStatus(repositoryId: number) {
  return request<CodeRepository>(`/api/repositories/${repositoryId}/index-status`)
}

export function getBranches(repositoryId: number) {
  return request<RepositoryBranch[]>(`/api/repositories/${repositoryId}/branches`)
}

export function refreshBranches(repositoryId: number) {
  return request<RepositoryBranch[]>(`/api/repositories/${repositoryId}/branches/refresh`, {
    method: 'POST',
  })
}
