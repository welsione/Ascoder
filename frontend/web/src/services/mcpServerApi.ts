import { request } from './httpClient'
import type { McpServer } from '../types/mcpServer'

export function getAll() {
  return request<McpServer[]>('/api/mcp-servers')
}

export function create(payload: {
  name: string
  description: string
  transport: string
  command: string
  argumentsJson: string
  endpointUrl: string
  headersJson: string
  queryParamsJson: string
  enabledToolsJson: string
  disabledToolsJson: string
  timeoutSeconds: number
  enabled: boolean
}) {
  return request<McpServer>('/api/mcp-servers', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateEnabled(serverId: number, enabled: boolean) {
  return request<McpServer>(`/api/mcp-servers/${serverId}/enabled`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ enabled }),
  })
}
