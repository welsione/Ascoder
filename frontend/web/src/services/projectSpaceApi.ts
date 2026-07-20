import { request } from './httpClient'
import type { ProjectSpace, ProjectSpaceMember } from '../types/projectSpace'

const LONG_TASK_TIMEOUT_MS = 10 * 60 * 1000
const PROGRESS_TIMEOUT_MS = 5000

export function getAll() {
  return request<ProjectSpace[]>('/api/project-spaces')
}

export function create(payload: {
  projectId: number
  name: string
  description?: string | null
  defaultBranch: string
  members?: Array<{
    repositoryId: number
    branchId?: number | null
    branchName?: string | null
    alias?: string | null
    role?: string | null
  }>
}) {
  return request<ProjectSpace>('/api/project-spaces', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function getMembers(projectSpaceId: number) {
  return request<ProjectSpaceMember[]>(`/api/project-spaces/${projectSpaceId}/members`)
}

export function prepare(projectSpaceId: number) {
  return request<ProjectSpace>(`/api/project-spaces/${projectSpaceId}/prepare`, {
    method: 'POST',
    timeoutMs: LONG_TASK_TIMEOUT_MS,
  })
}

export function index(projectSpaceId: number) {
  return request<ProjectSpace>(`/api/project-spaces/${projectSpaceId}/index`, {
    method: 'POST',
    timeoutMs: LONG_TASK_TIMEOUT_MS,
  })
}

export function reindex(projectSpaceId: number) {
  return request<ProjectSpace>(`/api/project-spaces/${projectSpaceId}/reindex`, {
    method: 'POST',
    timeoutMs: LONG_TASK_TIMEOUT_MS,
  })
}

export function refresh(projectSpaceId: number) {
  return request<ProjectSpace>(`/api/project-spaces/${projectSpaceId}/refresh`, {
    method: 'POST',
  })
}

export function pull(projectSpaceId: number) {
  return request<ProjectSpace>(`/api/project-spaces/${projectSpaceId}/pull`, {
    method: 'POST',
    timeoutMs: LONG_TASK_TIMEOUT_MS,
  })
}

export function getIndexProgress(projectSpaceId: number, signal?: AbortSignal) {
  return request<{ percent: number; message: string; completed: boolean }>(
    `/api/project-spaces/${projectSpaceId}/index-progress`,
    { signal, timeoutMs: PROGRESS_TIMEOUT_MS }
  )
}
export function remove(projectSpaceId: number) {
  return request<void>(`/api/project-spaces/${projectSpaceId}`, {
    method: 'DELETE',
  })
}
