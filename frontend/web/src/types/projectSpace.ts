import type { RepositoryBranchSourceKind } from './repository'

export type ProjectSpaceStatus =
  | 'CREATED'
  | 'PREPARING'
  | 'READY_TO_INDEX'
  | 'INDEXING'
  | 'READY'
  | 'STALE'
  | 'FAILED'

export type ProjectSpace = {
  id: number
  projectId: number
  project: string
  name: string
  description: string | null
  rootPath: string
  codegraphIndexPath: string | null
  status: ProjectSpaceStatus
  lastPreparedAt: string | null
  lastIndexedAt: string | null
  lastError: string | null
  createdAt: string
  updatedAt: string
}

export type ProjectSpaceCommit = {
  commitSha: string
  shortSha: string
  commitMessage: string
  committedAt: string
}

export type ProjectSpaceMember = {
  id: number
  projectSpaceId: number
  space: string
  repositoryId: number
  repositoryName: string
  repository?: string
  branchWorkspaceId: number | null
  branchId: number | null
  branchName: string
  branchRefName: string | null
  branchSourceKind: RepositoryBranchSourceKind | null
  alias: string
  role: string
  commitSha: string | null
  commitMessage: string | null
  remoteCommitSha: string | null
  remoteCommitMessage: string | null
  behindRemote: boolean
  recentCommits: ProjectSpaceCommit[]
  linkPath: string | null
  worktreePath: string | null
  createdAt: string
  updatedAt: string
}
