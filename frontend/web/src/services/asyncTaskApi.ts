import { request } from './httpClient'
import type { AsyncTask, AsyncTaskPage, TaskKind, TaskStatus } from '../types/asyncTask'

export function list(params: {
  page?: number
  size?: number
  sort?: string
  kind?: TaskKind
  status?: TaskStatus[]
}) {
  const query = new URLSearchParams()
  if (params.page !== undefined) query.set('page', String(params.page))
  if (params.size !== undefined) query.set('size', String(params.size))
  if (params.sort) query.set('sort', params.sort)
  if (params.kind) query.set('kind', params.kind)
  if (params.status?.length) {
    for (const s of params.status) query.append('status', s)
  }
  return request<AsyncTaskPage>(`/api/tasks?${query.toString()}`)
}

export function get(taskId: number) {
  return request<AsyncTask>(`/api/tasks/${taskId}`)
}

export function cancel(taskId: number) {
  return request<AsyncTask>(`/api/tasks/${taskId}/cancel`, { method: 'POST' })
}

export function retry(taskId: number) {
  return request<AsyncTask>(`/api/tasks/${taskId}/retry`, { method: 'POST' })
}

export function cleanupStaleTasks(staleThresholdHours = 24) {
  return request<number>(`/api/tasks/cleanup?staleThresholdHours=${staleThresholdHours}`, { method: 'POST' })
}
