import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '../services/repositoryApi'
import type { CodeRepository, RepositoryBranch, RepositoryBranchSourceKind, RepositoryStatus } from '../types/repository'
import { useCrudStore } from '../composables/useCrudStore'

interface RepositoryForm {
  name: string
  localPath: string
  remoteUrl: string
  defaultBranch: string
  authUsername: string
  authPassword: string
}

export const useRepositoryStore = defineStore('repository', () => {
  const crud = useCrudStore<CodeRepository, RepositoryForm>({
    initialForm: () => ({
      name: '',
      localPath: '',
      remoteUrl: '',
      defaultBranch: '',
      authUsername: '',
      authPassword: '',
    }),
  })

  const branchesByRepository = ref<Record<number, RepositoryBranch[]>>({})
  const indexingId = ref<number | null>(null)
  const syncingId = ref<number | null>(null)
  const branchLoadingId = ref<number | null>(null)
  const branchRefreshingId = ref<number | null>(null)

  const readyRepositories = computed(() =>
    crud.items.value.filter((repo) => repo.status === 'READY')
  )

  function statusType(status: RepositoryStatus) {
    if (status === 'READY') return 'success'
    if (status === 'FAILED') return 'danger'
    if (status === 'INDEXING') return 'warning'
    return 'info'
  }

  function sourceKindLabel(sourceKind: RepositoryBranchSourceKind) {
    const labels: Record<RepositoryBranchSourceKind, string> = {
      LOCAL_HEAD: '本地分支',
      REMOTE_HEAD: '远端分支',
      REMOTE_TRACKING: '远端跟踪',
    }
    return labels[sourceKind]
  }

  function sourceKindType(sourceKind: RepositoryBranchSourceKind) {
    if (sourceKind === 'LOCAL_HEAD') return 'success'
    if (sourceKind === 'REMOTE_TRACKING') return 'warning'
    return 'info'
  }

  function shortSha(commitSha?: string | null) {
    return commitSha ? commitSha.slice(0, 7) : 'unknown'
  }

  async function fetch() {
    await crud.fetchAll(() => api.getAll(), '无法加载仓库列表')
  }

  async function create() {
    return crud.create(
      (form) =>
        api.create({
          name: form.name.trim(),
          localPath: form.localPath.trim() || undefined,
          remoteUrl: form.remoteUrl.trim() || undefined,
          defaultBranch: form.defaultBranch.trim() || undefined,
          authUsername: form.authUsername.trim() || undefined,
          authPassword: form.authPassword.trim() || undefined,
        }),
      (form) => {
        if (!form.name.trim()) return '请填写仓库名称'
        if (!form.localPath.trim() && !form.remoteUrl.trim()) {
          return '本地仓库请填写 localPath，远程仓库请填写 remoteUrl'
        }
        return null
      },
      '无法添加仓库',
      async () => {
        crud.resetForm()
        await fetch()
      }
    ) as Promise<CodeRepository | null>
  }

  async function triggerIndex(repositoryId: number) {
    indexingId.value = repositoryId
    crud.error.value = ''
    try {
      const updated = await api.triggerIndex(repositoryId)
      updateRepository(updated)
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '索引失败'
    } finally {
      indexingId.value = null
    }
  }

  async function fetchRemote(repositoryId: number) {
    syncingId.value = repositoryId
    crud.error.value = ''
    try {
      const updated = await api.fetch(repositoryId)
      updateRepository(updated)
      await fetchBranches(repositoryId)
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '同步远程分支失败'
    } finally {
      syncingId.value = null
    }
  }

  async function pullRemote(repositoryId: number) {
    syncingId.value = repositoryId
    crud.error.value = ''
    try {
      const updated = await api.pull(repositoryId)
      updateRepository(updated)
      await fetchBranches(repositoryId)
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '拉取仓库更新失败'
    } finally {
      syncingId.value = null
    }
  }

  async function updateCredentials(
    repositoryId: number,
    authUsername: string,
    authPassword: string
  ) {
    crud.error.value = ''
    try {
      const updated = await api.updateCredentials(repositoryId, {
        authUsername: authUsername.trim() || undefined,
        authPassword: authPassword.trim() || undefined,
      })
      updateRepository(updated)
      return updated
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '更新凭据失败'
      return null
    }
  }

  async function fetchIndexStatus(repositoryId: number) {
    try {
      const updated = await api.getIndexStatus(repositoryId)
      updateRepository(updated)
      return updated
    } catch {
      return null
    }
  }

  async function fetchBranches(repositoryId: number) {
    branchLoadingId.value = repositoryId
    crud.error.value = ''
    try {
      branchesByRepository.value = {
        ...branchesByRepository.value,
        [repositoryId]: await api.getBranches(repositoryId),
      }
      return branchesByRepository.value[repositoryId]
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '无法加载仓库分支'
      branchesByRepository.value = {
        ...branchesByRepository.value,
        [repositoryId]: [],
      }
      return []
    } finally {
      branchLoadingId.value = null
    }
  }

  async function refreshBranches(repositoryId: number) {
    branchRefreshingId.value = repositoryId
    crud.error.value = ''
    try {
      branchesByRepository.value = {
        ...branchesByRepository.value,
        [repositoryId]: await api.refreshBranches(repositoryId),
      }
      return branchesByRepository.value[repositoryId]
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '刷新仓库分支失败'
      return []
    } finally {
      branchRefreshingId.value = null
    }
  }

  function updateRepository(updated: CodeRepository) {
    const idx = crud.items.value.findIndex((r) => r.id === updated.id)
    if (idx !== -1) {
      crud.items.value[idx] = updated
    }
  }

  return {
    repositories: crud.items,
    branchesByRepository,
    loading: crud.loading,
    error: crud.error,
    createLoading: crud.createLoading,
    indexingId,
    syncingId,
    branchLoadingId,
    branchRefreshingId,
    form: crud.form,
    readyRepositories,
    statusType,
    sourceKindLabel,
    sourceKindType,
    shortSha,
    fetch,
    create,
    triggerIndex,
    fetchRemote,
    pullRemote,
    updateCredentials,
    fetchIndexStatus,
    fetchBranches,
    refreshBranches,
    resetForm: crud.resetForm,
  }
})
