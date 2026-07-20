export type Project = {
  id: number
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export type ProjectRepositoryMember = {
  id: number
  projectId: number
  project: string
  repositoryId: number
  repository: string
  defaultBranch: string | null
  alias: string
  role: string
  primaryRepository: boolean
  sortOrder: number
  createdAt: string
}
