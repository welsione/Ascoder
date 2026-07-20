export type BranchWorkspaceStatus = 'CREATED' | 'PREPARING' | 'READY' | 'INDEXING' | 'STALE' | 'FAILED'

export type GitBranchResponse = {
  name: string
  commitSha: string
  workspaceStatus: BranchWorkspaceStatus | null
  workspaceId: number | null
}

export type BranchWorkspace = {
  id: number
  repositoryId: number
  repository: string
  branchName: string
  commitSha: string
  commitMessage: string | null
  worktreePath: string
  codegraphIndexPath: string
  status: BranchWorkspaceStatus
  lastIndexedAt: string | null
  lastIndexError: string | null
  createdAt: string
  updatedAt: string
}
