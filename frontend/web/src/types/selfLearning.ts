export type LearningRawEventType =
  | 'CONVERSATION_RECORD'
  | 'USER_QUESTION'
  | 'USER_FOLLOW_UP'
  | 'ASSISTANT_ANSWER'
  | 'AGENT_OUTPUT'
  | 'QUERY_PLAN'
  | 'DELEGATION_PLAN'
  | 'TOOL_CALL'
  | 'TOOL_RESULT'
  | 'CODE_EVIDENCE'
  | 'GIT_EVIDENCE'
  | 'USER_FEEDBACK'
  | 'USER_CORRECTION'
  | 'VERIFICATION_RESULT'

export type LearningKnowledgeType =
  | 'BUSINESS_CONTEXT'
  | 'GLOSSARY'
  | 'CODE_CONVENTION'
  | 'TROUBLESHOOTING'
  | 'ARCHITECTURE_DECISION'
  | 'BUG_FIX'
  | 'NEGATIVE_EXAMPLE'
  | 'QUESTION_ANSWER'
  | 'REQUIREMENT_LOGIC'
  | 'TEST_CONSIDERATION'

export type LearningInsightStatus = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'MERGED'

export type LearningKnowledgeStatus = 'ACTIVE' | 'VERIFIED' | 'STALE' | 'DEPRECATED' | 'REJECTED' | 'NEGATIVE'

export type SelfLearningSettings = {
  id: number
  projectSpaceId: number
  enabled: boolean
  autoCandidateEnabled: boolean
  rawEventCaptureEnabled: boolean
  autoInsightEnabled: boolean
  answerInjectionEnabled: boolean
  sourceVisibleEnabled: boolean
  adminReviewRequired: boolean
  createdAt: string
  updatedAt: string
}

export type SelfLearningSummary = {
  settings: SelfLearningSettings
  rawEventCount: number
  insightCount: number
  pendingInsightCount: number
  knowledgeItemCount: number
  experienceCount: number
  candidateCount: number
  correctionCount: number
  termCount: number
}

export type SelfLearningAgentRun = {
  runId: number | null
  status: LearningAgentRunStatus | null
  createdInsightCount: number
  consumedRawEventCount: number
  skippedRawEventCount: number
  failedConversationCount: number
  message: string
}

export type LearningAgentRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'PARTIAL_FAILED' | 'SKIPPED' | 'FAILED'

export type LearningAgentRunRecord = {
  id: number
  projectSpaceId: number
  status: LearningAgentRunStatus
  limitCount: number
  createdInsightCount: number
  consumedRawEventCount: number
  skippedRawEventCount: number
  failedConversationCount: number
  currentRawEventIdsJson: string | null
  failureDetailsJson: string | null
  message: string | null
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
}

export type ImportHistoryRawEventsResult = {
  importedConversationCount: number
  importedRawEventCount: number
  skippedRawEventCount: number
  message: string
}

export type CleanupLegacyRawEventsResult = {
  deletedRawEventCount: number
  deletedInsightCount: number
  staleKnowledgeItemCount: number
  message: string
}

export type CleanupLegacyInsightsResult = {
  deletedInsightCount: number
  staleKnowledgeItemCount: number
  message: string
}

export type LearningRawEvent = {
  id: number
  projectSpaceId: number
  repositoryId: number | null
  branchName: string | null
  questionId: number | null
  conversationId: number | null
  agentId: string | null
  eventType: LearningRawEventType
  eventPayloadJson: string | null
  summary: string | null
  evidenceJson: string | null
  gitProvenanceJson: string | null
  userFeedbackType: string | null
  sourceCreatedAt: string | null
  createdAt: string
}

export type LearningInsight = {
  id: number
  projectSpaceId: number
  repositoryId: number | null
  sourceRawEventIdsJson: string | null
  sourceQuestionIdsJson: string | null
  type: LearningKnowledgeType
  status: LearningInsightStatus
  title: string
  summary: string | null
  conclusion: string
  businessContext: string | null
  glossaryMappingsJson: string | null
  codeSymbolsJson: string | null
  warnings: string | null
  applicableScope: string | null
  evidenceJson: string | null
  gitProvenanceJson: string | null
  tags: string | null
  confidence: number
  reviewerId: number | null
  reviewComment: string | null
  reviewedAt: string | null
  createdAt: string
  updatedAt: string
}

export type LearningInsightVerificationStatus =
  | 'VERIFIED'
  | 'NEEDS_CHANGES'
  | 'INSUFFICIENT_EVIDENCE'
  | 'CONTRADICTED'
  | string

export type LearningInsightVerification = {
  insightId: number
  status: LearningInsightVerificationStatus
  summary: string | null
  codeEvidenceJson: string | null
  gitProvenanceJson: string | null
  suggestedWarnings: string | null
  suggestedChanges: string | null
  confidence: number | null
  verifiedAt: string
}

export type RefineLearningInsightResult = {
  insightId: number
  suggestion: SaveLearningInsightPayload
  assistantMessage: string
}

export type LearningKnowledgeItem = {
  id: number
  projectSpaceId: number
  repositoryId: number | null
  sourceInsightIdsJson: string | null
  sourceRawEventIdsJson: string | null
  type: LearningKnowledgeType
  status: LearningKnowledgeStatus
  title: string
  content: string
  summary: string | null
  applicableScope: string | null
  evidenceJson: string | null
  gitProvenanceJson: string | null
  tags: string | null
  confidence: number
  usageCount: number
  acceptedCount: number
  rejectedCount: number
  lastUsedAt: string | null
  staleReason: string | null
  reviewerId: number | null
  createdAt: string
  updatedAt: string
}

export type SaveLearningInsightPayload = {
  repositoryId?: number | null
  sourceRawEventIdsJson?: string | null
  sourceQuestionIdsJson?: string | null
  type?: LearningKnowledgeType
  status?: LearningInsightStatus
  title: string
  summary?: string | null
  conclusion: string
  businessContext?: string | null
  glossaryMappingsJson?: string | null
  codeSymbolsJson?: string | null
  warnings?: string | null
  applicableScope?: string | null
  evidenceJson?: string | null
  gitProvenanceJson?: string | null
  tags?: string | null
  confidence?: number
}

export type SaveLearningKnowledgeItemPayload = {
  repositoryId?: number | null
  sourceInsightIdsJson?: string | null
  sourceRawEventIdsJson?: string | null
  type?: LearningKnowledgeType
  status?: LearningKnowledgeStatus
  title: string
  content: string
  summary?: string | null
  applicableScope?: string | null
  evidenceJson?: string | null
  gitProvenanceJson?: string | null
  tags?: string | null
  confidence?: number
}
