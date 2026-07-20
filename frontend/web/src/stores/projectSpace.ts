import { defineStore } from 'pinia'
import { onScopeDispose, reactive, ref } from 'vue'
import * as api from '../services/projectSpaceApi'
import type { ProjectRepositoryMember } from '../types/project'
import type { ProjectSpace, ProjectSpaceMember, ProjectSpaceStatus } from '../types/projectSpace'
import { useCrudStore } from '../composables/useCrudStore'

interface MemberBranch {
  repositoryId: number
  repositoryName: string
  alias: string
  role: string
  branchId: number | null
  branchName: string
  branchRefName: string | null
  branchSourceKind: string | null
  commitSha: string | null
}

interface ProjectSpaceForm {
  projectId: number | null
  name: string
  description: string
  defaultBranch: string
  memberBranches: MemberBranch[]
}

export const useProjectSpaceStore = defineStore('projectSpace', () => {
  const crud = useCrudStore<ProjectSpace, ProjectSpaceForm>({
    initialForm: () => ({
      projectId: null,
      name: '',
      description: '',
      defaultBranch: '',
      memberBranches: [],
    }),
    trackSelected: true,
  })

  const members = ref<ProjectSpaceMember[]>([])
  const preparingId = ref<number | null>(null)
  const indexingId = ref<number | null>(null)
  const refreshingId = ref<number | null>(null)
  const pullingId = ref<number | null>(null)
  const deletingId = ref<number | null>(null)
  const indexProgress = ref<{ percent: number; message: string; completed: boolean } | null>(null)
  let indexProgressTimer: ReturnType<typeof setInterval> | null = null
  let indexProgressAbortController: AbortController | null = null
  let indexingResetTimer: ReturnType<typeof setTimeout> | null = null

  function statusType(status: ProjectSpaceStatus) {
    if (status === 'READY') return 'success'
    if (status === 'FAILED') return 'danger'
    if (status === 'INDEXING' || status === 'PREPARING') return 'warning'
    if (status === 'STALE') return 'warning'
    return 'info'
  }

  function statusLabel(status: ProjectSpaceStatus) {
    const labels: Record<ProjectSpaceStatus, string> = {
      CREATED: '待准备',
      PREPARING: '正在准备',
      READY_TO_INDEX: '待索引',
      INDEXING: '正在索引',
      READY: '可提问',
      STALE: '需更新',
      FAILED: '失败',
    }
    return labels[status]
  }

  async function fetch() {
    await crud.fetchAll(() => api.getAll(), '无法加载项目空间')
    if (!crud.selectedId.value && crud.items.value.length) {
      crud.selectedId.value = crud.items.value[0].id
      await fetchMembers(crud.selectedId.value)
    }
  }

  function generateMembers(projectMembers: ProjectRepositoryMember[]) {
    crud.form.memberBranches = projectMembers.map((member) => ({
      repositoryId: member.repositoryId,
      repositoryName: member.repository,
      alias: member.alias,
      role: member.role,
      branchId: null,
      branchName: member.defaultBranch || crud.form.defaultBranch || 'master',
      branchRefName: null,
      branchSourceKind: null,
      commitSha: null,
    }))
  }

  async function create() {
    return crud.create(
      (form) => {
        const fallbackBranch = form.defaultBranch.trim() || 'master'
        return api.create({
          projectId: form.projectId!, // validateFn 已确保非空
          name: form.name.trim(),
          description: form.description.trim() || null,
          defaultBranch: fallbackBranch,
          members: form.memberBranches.map((member) => ({
            repositoryId: member.repositoryId,
            branchId: member.branchId,
            branchName: member.branchName.trim() || fallbackBranch,
            alias: member.alias,
            role: member.role,
          })),
        })
      },
      (form) => {
        if (!form.projectId) return '请选择项目'
        if (!form.name.trim()) return '请填写项目空间名称'
        return null
      },
      '创建项目空间失败',
      async (space) => {
        crud.items.value.unshift(space)
        crud.selectedId.value = space.id
        await fetchMembers(space.id)
        crud.form.name = ''
        crud.form.description = ''
        crud.form.memberBranches = []
      }
    )
  }

  async function fetchMembers(projectSpaceId: number) {
    crud.selectedId.value = projectSpaceId
    crud.error.value = ''
    try {
      members.value = await api.getMembers(projectSpaceId)
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '无法加载项目空间成员'
      members.value = []
    }
  }

  async function prepare(projectSpaceId: number) {
    preparingId.value = projectSpaceId
    crud.error.value = ''
    try {
      const updated = await api.prepare(projectSpaceId)
      const idx = crud.items.value.findIndex((space) => space.id === projectSpaceId)
      if (idx !== -1) {
        crud.items.value[idx] = updated
      }
      crud.selectedId.value = projectSpaceId
      await fetchMembers(projectSpaceId)
      return updated
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '准备项目空间失败'
      return null
    } finally {
      preparingId.value = null
    }
  }

  async function index(projectSpaceId: number) {
    return runIndex(projectSpaceId, () => api.index(projectSpaceId))
  }

  /**
   * 重新索引：调用 reindex 端点（后端先删除旧索引再全量重建）。
   * 复用与 index 相同的进度轮询与状态管理。
   */
  async function reindex(projectSpaceId: number) {
    return runIndex(projectSpaceId, () => api.reindex(projectSpaceId))
  }

  /**
   * 索引执行的公共逻辑：启动进度轮询、调用索引 API、更新空间数据。
   * index 与 reindex 共用，避免重复代码。
   */
  async function runIndex(projectSpaceId: number, invoke: () => Promise<ProjectSpace>) {
    stopIndexProgressPolling()
    clearIndexingResetTimer()
    indexingId.value = projectSpaceId
    crud.error.value = ''
    indexProgress.value = { percent: 0, message: '开始索引', completed: false }

    // 启动轮询
    indexProgressTimer = setInterval(async () => {
      try {
        indexProgressAbortController?.abort()
        indexProgressAbortController = new AbortController()
        const progress = await api.getIndexProgress(projectSpaceId, indexProgressAbortController.signal)
        indexProgress.value = progress
        if (progress.completed) {
          stopIndexProgressPolling()
        }
      } catch {
        // 忽略轮询错误
      }
    }, 1000)

    try {
      const updated = await invoke()
      const idx = crud.items.value.findIndex((space) => space.id === projectSpaceId)
      if (idx !== -1) {
        crud.items.value[idx] = updated
      }
      crud.selectedId.value = projectSpaceId
      return updated
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '索引项目空间失败'
      return null
    } finally {
      stopIndexProgressPolling()
      // 延迟重置 indexingId，让用户能看到完成状态
      indexingResetTimer = setTimeout(() => {
        indexingId.value = null
        indexingResetTimer = null
      }, 3000)
    }
  }

  function stopIndexProgressPolling() {
    if (indexProgressTimer) {
      clearInterval(indexProgressTimer)
      indexProgressTimer = null
    }
    indexProgressAbortController?.abort()
    indexProgressAbortController = null
  }

  function clearIndexingResetTimer() {
    if (indexingResetTimer) {
      clearTimeout(indexingResetTimer)
      indexingResetTimer = null
    }
  }

  onScopeDispose(() => {
    stopIndexProgressPolling()
    clearIndexingResetTimer()
  })

  async function prepareAndIndex(projectSpaceId: number) {
    const prepared = await prepare(projectSpaceId)
    if (!prepared) return null
    if (prepared.status === 'READY_TO_INDEX' || prepared.status === 'READY' || prepared.status === 'STALE') {
      return index(projectSpaceId)
    }
    return prepared
  }

  async function refresh(projectSpaceId: number) {
    refreshingId.value = projectSpaceId
    crud.error.value = ''
    try {
      const updated = await api.refresh(projectSpaceId)
      const idx = crud.items.value.findIndex((space) => space.id === projectSpaceId)
      if (idx !== -1) {
        crud.items.value[idx] = updated
      }
      crud.selectedId.value = projectSpaceId
      await fetchMembers(projectSpaceId)
      return updated
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '刷新项目空间失败'
      return null
    } finally {
      refreshingId.value = null
    }
  }

  async function pullRemote(projectSpaceId: number) {
    pullingId.value = projectSpaceId
    crud.error.value = ''
    try {
      const updated = await api.pull(projectSpaceId)
      const idx = crud.items.value.findIndex((space) => space.id === projectSpaceId)
      if (idx !== -1) {
        crud.items.value[idx] = updated
      }
      crud.selectedId.value = projectSpaceId
      await fetchMembers(projectSpaceId)
      return updated
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '拉取项目空间代码失败'
      return null
    } finally {
      pullingId.value = null
    }
  }

  async function remove(projectSpaceId: number) {
    deletingId.value = projectSpaceId
    crud.error.value = ''
    try {
      await api.remove(projectSpaceId)
      crud.items.value = crud.items.value.filter((space) => space.id !== projectSpaceId)
      if (crud.selectedId.value === projectSpaceId) {
        crud.selectedId.value = crud.items.value[0]?.id ?? null
        members.value = []
        if (crud.selectedId.value) {
          await fetchMembers(crud.selectedId.value)
        }
      }
      return true
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '删除项目空间失败'
      return false
    } finally {
      deletingId.value = null
    }
  }

  return {
    spaces: crud.items,
    members,
    selectedSpaceId: crud.selectedId,
    selectedSpace: crud.selected,
    loading: crud.loading,
    preparingId,
    indexingId,
    refreshingId,
    pullingId,
    deletingId,
    error: crud.error,
    indexProgress,
    form: crud.form,
    statusType,
    statusLabel,
    fetch,
    generateMembers,
    create,
    fetchMembers,
    prepare,
    index,
    reindex,
    prepareAndIndex,
    refresh,
    pullRemote,
    remove,
  }
})
