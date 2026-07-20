export type AgentRole = 'ORCHESTRATOR' | 'SPECIALIST' | 'PLANNER' | 'SELF_LEARNING'
export type SpecialistTaskKind = 'CODE_RESEARCH' | 'IMPACT_ANALYSIS' | 'PRODUCT_REVIEW' | 'TEST_REVIEW'
export type AgentRuntimeStatus = 'IDLE' | 'RUNNING'
export type AgentRunStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'INTERRUPTED'

export type AgentConfig = {
  id: number
  agentId: string
  displayName: string
  description: string | null
  agentRole: AgentRole
  taskKind: SpecialistTaskKind | null
  systemPrompt: string
  taskTemplate: string | null
  maxIters: number
  maxTokens: number | null
  timeoutSeconds: number | null
  modelId: string | null
  llmProviderId: number | null
  roleKeys: string[]
  questionKeywords: string[]
  toolGroupKeys: string[]
  skillNames: string[]
  mcpServerNames: string[]
  required: boolean
  enabled: boolean
  builtin: boolean
  handoffTitle: string | null
  handoffDescription: string | null
  returnTitle: string | null
  returnDescription: string | null
  sortOrder: number
  createdAt: string
  updatedAt: string
  runtimeStatus: AgentRuntimeStatus
}

export type AgentRunRecord = {
  id: number
  agentId: string
  agentConfigId: number
  questionId: number | null
  conversationId: number | null
  attemptNo: number
  status: AgentRunStatus
  inputSummary: string | null
  outputSummary: string | null
  toolCallCount: number
  iterCount: number
  errorMessage: string | null
  startedAt: string
  finishedAt: string | null
  durationMs: number | null
  createdAt: string
}

export type AgentRuntimeView = {
  agentId: string
  status: AgentRuntimeStatus
  questionId: number | null
  runRecordId: number | null
  startedAt: string | null
}

export type CreateAgentConfigRequest = {
  agentId: string
  displayName: string
  description?: string
  agentRole: AgentRole
  taskKind?: SpecialistTaskKind
  systemPrompt: string
  taskTemplate?: string
  maxIters: number
  maxTokens?: number
  timeoutSeconds?: number
  modelId?: string
  llmProviderId?: number | null
  roleKeys?: string[]
  questionKeywords?: string[]
  toolGroupKeys?: string[]
  skillNames?: string[]
  mcpServerNames?: string[]
  required?: boolean
  enabled?: boolean
  handoffTitle?: string
  handoffDescription?: string
  returnTitle?: string
  returnDescription?: string
  sortOrder?: number
}

export type UpdateAgentConfigRequest = CreateAgentConfigRequest

export type TestRenderRequest = {
  sampleContext: Record<string, unknown> | null
}

export type TestRenderResponse = {
  renderedText: string
  warnings: string[]
}

export type AgentPage = {
  content: AgentRunRecord[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
