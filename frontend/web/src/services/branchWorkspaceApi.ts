import { request } from './httpClient'
import type { BranchWorkspace, GitBranchResponse } from '../types/branchWorkspace'

export function getAll(repositoryId?: number) {
  const query = repositoryId ? `?repositoryId=${repositoryId}` : ''
  return request<BranchWorkspace[]>(`/api/branch-workspaces${query}`)
}

export function getBranches(repositoryId: number) {
  return request<GitBranchResponse[]>(`/api/repositories/${repositoryId}/git-branches`)
}

export function create(repositoryId: number, payload: { branchName: string }) {
  return request<BranchWorkspace>(`/api/repositories/${repositoryId}/branch-workspaces`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function get(id: number) {
  return request<BranchWorkspace>(`/api/branch-workspaces/${id}`)
}

export function index(id: number) {
  return request<BranchWorkspace>(`/api/branch-workspaces/${id}/index`, {
    method: 'POST',
  })
}

export function refresh(id: number) {
  return request<BranchWorkspace>(`/api/branch-workspaces/${id}/refresh`, {
    method: 'POST',
  })
}

export function remove(id: number) {
  return request<void>(`/api/branch-workspaces/${id}`, {
    method: 'DELETE',
  })
}
