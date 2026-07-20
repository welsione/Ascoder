import { computed } from 'vue'
import { defineStore } from 'pinia'
import * as api from '../services/agentToolApi'
import type { AgentTool } from '../types/agentTool'
import { useCrudStore } from '../composables/useCrudStore'

/** agentTool 无表单，使用空对象占位 */
type EmptyForm = Record<string, never>

export const useAgentToolStore = defineStore('agentTool', () => {
  const crud = useCrudStore<AgentTool, EmptyForm>({
    initialForm: () => ({}),
  })

  const enabledCount = computed(() => crud.items.value.filter((tool) => tool.enabled).length)
  const totalCount = computed(() => crud.items.value.length)
  const groupedTools = computed(() => {
    const groups = new Map<string, AgentTool[]>()
    for (const tool of crud.items.value) {
      const list = groups.get(tool.groupName) ?? []
      list.push(tool)
      groups.set(tool.groupName, list)
    }
    return Array.from(groups.entries()).map(([name, items]) => ({
      name,
      tools: items,
      enabledCount: items.filter((tool) => tool.enabled).length,
    }))
  })

  async function fetch() {
    await crud.fetchAll(() => api.getAll(), '无法加载 Agent 工具')
  }

  async function toggleEnabled(toolId: number, enabled: boolean) {
    crud.error.value = ''
    crud.busyIds.value = new Set(crud.busyIds.value).add(toolId)
    try {
      const updated = await api.updateEnabled(toolId, enabled)
      const idx = crud.items.value.findIndex((tool) => tool.id === toolId)
      if (idx !== -1) {
        crud.items.value[idx] = updated
      }
    } catch (err) {
      crud.error.value = err instanceof Error ? err.message : '无法更新工具状态'
      await fetch()
    } finally {
      const next = new Set(crud.busyIds.value)
      next.delete(toolId)
      crud.busyIds.value = next
    }
  }

  function isUpdating(toolId: number) {
    return crud.isBusy(toolId)
  }

  return {
    tools: crud.items,
    loading: crud.loading,
    error: crud.error,
    enabledCount,
    totalCount,
    groupedTools,
    fetch,
    toggleEnabled,
    isUpdating,
  }
})
