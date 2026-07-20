import { request } from './httpClient'
import type { RuntimeSetting, UpdateRuntimeSettingRequest } from '../types/runtimeSetting'

export function listAll() {
  return request<RuntimeSetting[]>('/api/settings')
}

export function listByCategory(category: string) {
  return request<RuntimeSetting[]>(`/api/settings/${category}`)
}

export function update(key: string, payload: UpdateRuntimeSettingRequest) {
  return request<RuntimeSetting>(`/api/settings/${encodeURIComponent(key)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function reset(category: string) {
  return request<void>(`/api/settings/reset/${category}`, { method: 'POST' })
}