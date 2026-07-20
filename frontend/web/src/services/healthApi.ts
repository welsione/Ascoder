import { request } from './httpClient'
import type { HealthResponse } from '../types/common'

export function getHealth() {
  return request<HealthResponse>('/api/health')
}
