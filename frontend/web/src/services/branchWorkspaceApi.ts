/**
 * 分支工作区 API。
 *
 * @deprecated 前端未接入分支 workspace 管理 UI，所有方法均无调用方，预留暂不维护。
 */
import { request } from './httpClient'
import type { BranchWorkspace, GitBranchResponse } from '../types/branchWorkspace'

/** @deprecated 无调用方，预留暂不维护。 */
export function getAll(repositoryId?: number) {
  const query = repositoryId ? `?repositoryId=${repositoryId}` : ''
  return request<BranchWorkspace[]>(`/api/branch-workspaces${query}`)
}

/** @deprecated 无调用方，预留暂不维护。 */
export function getBranches(repositoryId: number) {
  return request<GitBranchResponse[]>(`/api/repositories/${repositoryId}/git-branches`)
}

/** @deprecated 无调用方，预留暂不维护。 */
export function create(repositoryId: number, payload: { branchName: string }) {
  return request<BranchWorkspace>(`/api/repositories/${repositoryId}/branch-workspaces`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

/** @deprecated 无调用方，预留暂不维护。 */
export function get(id: number) {
  return request<BranchWorkspace>(`/api/branch-workspaces/${id}`)
}

/** @deprecated 无调用方，预留暂不维护。 */
export function index(id: number) {
  return request<BranchWorkspace>(`/api/branch-workspaces/${id}/index`, {
    method: 'POST',
  })
}

/** @deprecated 无调用方，预留暂不维护。 */
export function refresh(id: number) {
  return request<BranchWorkspace>(`/api/branch-workspaces/${id}/refresh`, {
    method: 'POST',
  })
}

/** @deprecated 无调用方，预留暂不维护。 */
export function remove(id: number) {
  return request<void>(`/api/branch-workspaces/${id}`, {
    method: 'DELETE',
  })
}
