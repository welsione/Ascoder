export type RepositoryStatus = 'CREATED' | 'INDEXING' | 'READY' | 'FAILED'
export type RepositoryBranchSourceKind = 'LOCAL_HEAD' | 'REMOTE_HEAD' | 'REMOTE_TRACKING'

export type CodeRepository = {
  id: number
  name: string
  localPath: string
  remoteUrl: string | null
  defaultBranch: string | null
  hasCredentials: boolean
  authUsername: string | null
  status: RepositoryStatus
  lastIndexedAt: string | null
  lastPulledAt: string | null
  lastIndexError: string | null
  lastPullError: string | null
  createdAt: string
  updatedAt: string
}

export type RepositoryBranch = {
  id: number
  repositoryId: number
  repositoryName: string
  name: string
  refName: string
  commitSha: string
  remoteName: string | null
  sourceKind: RepositoryBranchSourceKind
  active: boolean
  lastSeenAt: string | null
  createdAt: string
  updatedAt: string
}
