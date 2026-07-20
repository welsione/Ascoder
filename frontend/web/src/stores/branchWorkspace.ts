import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import * as api from '../services/branchWorkspaceApi'
import type { BranchWorkspace, GitBranchResponse, BranchWorkspaceStatus } from '../types/branchWorkspace'
import { useCrudStore } from '../composables/useCrudStore'

interface BranchWorkspaceForm {
  branchName: string
}

export const useBranchWorkspaceStore = defineStore('branchWorkspace', () => {
  const crud = useCrudStore<BranchWorkspace, BranchWorkspaceForm>({
    initialForm: () => ({ branchName: '' }),
  })

  const branches = ref<GitBranchResponse[]>([])
  const selectedWorkspaceId = ref<number | null>(null)
  const indexingWorkspaceId = ref<number | null>(null)
  const refreshingWorkspaceId = ref<number | null>(null)
  const deletingWorkspaceId = ref<number | null>(null)

  function statusType(status: BranchWorkspaceStatus | null) {
    if (!status) return 'info'
    if (status === 'READY') return 'success'
    if (status === 'FAILED') return 'danger'
    if (status === 'INDEXING' || status === 'PREPARING') return 'warning'
    return 'info'
  }

  async function fetch(repositoryId?: number) {
    await crud.fetchAll(() => api.getAll(repositoryId), '无法加载工作空间')
  }

  async function fetchBranches(repositoryId: number) {
    crud.loading.value = true
    crud.error.value = ''
    try {
      branches.value = await api.getBranches(repositoryId)
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '无法加载分支列表'
      branches.value = []
    } finally {
      crud.loading.value = false
    }
  }

  async function create(repositoryId: number, branchName: string) {
    crud.form.branchName = branchName
    return crud.create(
      () => api.create(repositoryId, { branchName }),
      (form) => (!form.branchName.trim() ? '请填写分支名称' : null),
      '无法创建分支工作区',
      async (workspace) => {
        crud.items.value.push(workspace)
      }
    )
  }

  async function refresh(workspaceId: number) {
    refreshingWorkspaceId.value = workspaceId
    crud.error.value = ''
    try {
      const updated = await api.refresh(workspaceId)
      upsertWorkspace(updated)
      return updated
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '刷新工作空间失败'
      return null
    } finally {
      refreshingWorkspaceId.value = null
    }
  }

  async function remove(workspaceId: number) {
    deletingWorkspaceId.value = workspaceId
    crud.error.value = ''
    try {
      await api.remove(workspaceId)
      crud.items.value = crud.items.value.filter((workspace) => workspace.id !== workspaceId)
      branches.value = branches.value.map((branch) =>
        branch.workspaceId === workspaceId
          ? { ...branch, workspaceStatus: null, workspaceId: null }
          : branch
      )
      if (selectedWorkspaceId.value === workspaceId) {
        selectedWorkspaceId.value = null
      }
      return true
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '删除工作空间失败'
      return false
    } finally {
      deletingWorkspaceId.value = null
    }
  }

  async function triggerIndex(workspaceId: number) {
    indexingWorkspaceId.value = workspaceId
    crud.error.value = ''
    try {
      const updated = await api.index(workspaceId)
      upsertWorkspace(updated)
      // 更新 branches 中的状态
      const branchIdx = branches.value.findIndex(
        (b) => b.workspaceId === workspaceId
      )
      if (branchIdx !== -1) {
        branches.value[branchIdx] = {
          ...branches.value[branchIdx],
          workspaceStatus: updated.status,
        }
      }
      return updated
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '索引失败'
      return null
    } finally {
      indexingWorkspaceId.value = null
    }
  }

  async function get(id: number) {
    try {
      return await api.get(id)
    } catch {
      return null
    }
  }

  function setSelectedWorkspace(id: number | null) {
    selectedWorkspaceId.value = id
  }

  function clearBranches() {
    branches.value = []
  }

  function upsertWorkspace(updated: BranchWorkspace) {
    const idx = crud.items.value.findIndex((workspace) => workspace.id === updated.id)
    if (idx === -1) {
      crud.items.value.push(updated)
    } else {
      crud.items.value[idx] = updated
    }
  }

  return {
    branches,
    workspaces: crud.items,
    loading: crud.loading,
    error: crud.error,
    selectedWorkspaceId,
    indexingWorkspaceId,
    refreshingWorkspaceId,
    deletingWorkspaceId,
    form: crud.form,
    statusType,
    fetch,
    fetchBranches,
    create,
    refresh,
    remove,
    triggerIndex,
    get,
    setSelectedWorkspace,
    clearBranches,
  }
})
