export type McpTransport = 'STDIO' | 'SSE' | 'HTTP'

export type McpServer = {
  id: number
  name: string
  description: string | null
  transport: McpTransport
  command: string | null
  argumentsJson: string | null
  endpointUrl: string | null
  headersJson: string | null
  queryParamsJson: string | null
  enabledToolsJson: string | null
  disabledToolsJson: string | null
  timeoutSeconds: number
  enabled: boolean
  lastError: string | null
  createdAt: string
  updatedAt: string
}
