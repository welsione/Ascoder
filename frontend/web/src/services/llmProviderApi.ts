import { request } from './httpClient'
import type { LlmProvider, CreateLlmProviderRequest, UpdateLlmProviderRequest, ConnectionTestResponse } from '../types/llmProvider'

export function list() {
  return request<LlmProvider[]>('/api/llm-providers')
}

export function getById(id: number) {
  return request<LlmProvider>(`/api/llm-providers/${id}`)
}

export function create(payload: CreateLlmProviderRequest) {
  return request<LlmProvider>('/api/llm-providers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function update(id: number, payload: UpdateLlmProviderRequest) {
  return request<LlmProvider>(`/api/llm-providers/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function remove(id: number) {
  return request<void>(`/api/llm-providers/${id}`, { method: 'DELETE' })
}

export function testConnection(id: number) {
  return request<ConnectionTestResponse>(`/api/llm-providers/${id}/test`, { method: 'POST' })
}

export function setDefault(id: number) {
  return request<LlmProvider>(`/api/llm-providers/${id}/default`, { method: 'PUT' })
}

export function updateEnabled(id: number, enabled: boolean) {
  return request<LlmProvider>(`/api/llm-providers/${id}/enabled`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}
