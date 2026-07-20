export type LlmProviderType = 'ANTHROPIC_COMPATIBLE' | 'OPENAI_COMPATIBLE'

export type LlmProvider = {
  id: number
  name: string
  providerType: LlmProviderType
  apiKey: string
  baseUrl: string
  modelId: string
  maxTokens: number | null
  timeoutSeconds: number | null
  isDefault: boolean
  enabled: boolean
  builtin: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
}

export type CreateLlmProviderRequest = {
  name: string
  providerType: LlmProviderType
  apiKey: string
  baseUrl: string
  modelId: string
  maxTokens?: number
  timeoutSeconds?: number
  isDefault?: boolean
  enabled?: boolean
}

export type UpdateLlmProviderRequest = CreateLlmProviderRequest

export type ConnectionTestResponse = {
  success: boolean
  message: string
  latencyMs: number | null
}
