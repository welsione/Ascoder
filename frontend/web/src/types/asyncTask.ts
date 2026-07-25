export type TaskKind =
  | 'GIT_CLONE'
  | 'GIT_FETCH'
  | 'CODEGRAPH_INDEX'
  | 'CODEGRAPH_SYNC'
  | 'PROJECT_SPACE_PREPARE'
  | 'BRANCH_REFRESH'

export type TaskStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'

export type AsyncTask = {
  id: number
  kind: TaskKind
  status: TaskStatus
  businessId: number | null
  /** businessId 的可读标签，如 "ascoder (仓库)"。 */
  businessLabel: string | null
  progress: number
  statusMessage: string | null
  errorMessage: string | null
  maxRetries: number
  retryCount: number
  timeoutMs: number
  queuedAt: string
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
  updatedAt: string
  version: number
}

export type AsyncTaskPage = {
  content: AsyncTask[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
