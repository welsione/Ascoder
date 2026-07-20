export type QuestionStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export type QueryPlanRecord = {
  id: number
  questionId: number
  type: string
  rewrittenQueries: string[]
  recommendedTools: string[]
  recommendedSkills: string[]
  reasoning: string | null
  createdAt: string
}

export type AnswerEvidenceRecord = {
  title: string
  reference: string
  detail: string
}

export type QuestionLogUploadRecord = {
  id: number
  originalFilename: string
  fileType: string
  fileSize: number
  status: string
  fileNames: string[]
}

export type QuestionRecord = {
  id: number
  conversationId: number | null
  conversationTitle: string | null
  projectSpaceId: number | null
  projectSpaceName: string | null
  repositoryId: number | null
  repositoryName: string | null
  branchWorkspaceId: number | null
  branchName: string | null
  commitSha: string | null
  text: string
  role: string | null
  status: QuestionStatus
  answer: string | null
  answerSummary: string | null
  answerEvidence: AnswerEvidenceRecord[]
  analysisProcess: string | null
  uncertainty: string | null
  nextStep: string | null
  codegraphContext: string | null
  logUploadIds: number[]
  logUploads: QuestionLogUploadRecord[]
  queryPlan: QueryPlanRecord | null
  errorMessage: string | null
  startedAt: string | null
  completedAt: string | null
  createdAt: string
}
