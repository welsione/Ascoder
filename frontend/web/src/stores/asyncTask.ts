import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as api from '../services/asyncTaskApi'
import type { AsyncTask, TaskKind, TaskStatus } from '../types/asyncTask'

export const useAsyncTaskStore = defineStore('asyncTask', () => {
  const tasks = ref<AsyncTask[]>([])
  const totalElements = ref(0)
  const totalPages = ref(0)
  const currentPage = ref(0)
  const pageSize = ref(20)
  const loading = ref(false)
  const error = ref('')
  const filterKind = ref<TaskKind | ''>('')
  const filterStatus = ref<TaskStatus[]>([])
  const cancellingIds = ref<Set<number>>(new Set())

  /** 活跃任务数（QUEUED + RUNNING），供侧边栏统计用。 */
  const activeCount = computed(() =>
    tasks.value.filter((t) => t.status === 'QUEUED' || t.status === 'RUNNING').length
  )

  /** 是否存在运行中的任务。 */
  const hasRunning = computed(() =>
    tasks.value.some((t) => t.status === 'RUNNING')
  )

  async function fetchTasks() {
    loading.value = true
    error.value = ''
    try {
      const page = await api.list({
        page: currentPage.value,
        size: pageSize.value,
        sort: 'queuedAt,desc',
        kind: filterKind.value || undefined,
        status: filterStatus.value.length > 0 ? filterStatus.value : undefined,
      })
      tasks.value = page.content
      totalElements.value = page.totalElements
      totalPages.value = page.totalPages
      currentPage.value = page.number
    } catch (err) {
      error.value = err instanceof Error ? err.message : '无法加载任务列表'
    } finally {
      loading.value = false
    }
  }

  function changePage(page: number) {
    currentPage.value = page
    fetchTasks()
  }

  function changePageSize(size: number) {
    pageSize.value = size
    currentPage.value = 0
    fetchTasks()
  }

  function setFilterKind(kind: TaskKind | '') {
    filterKind.value = kind
    currentPage.value = 0
    fetchTasks()
  }

  function setFilterStatus(statuses: TaskStatus[]) {
    filterStatus.value = statuses
    currentPage.value = 0
    fetchTasks()
  }

  async function cancelTask(taskId: number) {
    cancellingIds.value = new Set([...cancellingIds.value, taskId])
    try {
      await api.cancel(taskId)
      await fetchTasks()
    } catch (err) {
      error.value = err instanceof Error ? err.message : '取消任务失败'
    } finally {
      const next = new Set(cancellingIds.value)
      next.delete(taskId)
      cancellingIds.value = next
    }
  }

  function refresh() {
    fetchTasks()
  }

  return {
    tasks,
    totalElements,
    totalPages,
    currentPage,
    pageSize,
    loading,
    error,
    filterKind,
    filterStatus,
    cancellingIds,
    activeCount,
    hasRunning,
    fetchTasks,
    changePage,
    changePageSize,
    setFilterKind,
    setFilterStatus,
    cancelTask,
    refresh,
  }
})
