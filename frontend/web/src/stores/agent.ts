import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import * as api from '../services/agentApi'
import type {
  AgentConfig,
  AgentRuntimeStatus,
  AgentRunRecord,
  AgentPage,
  CreateAgentConfigRequest,
} from '../types/agent'
import { useCrudStore } from '../composables/useCrudStore'

export const useAgentStore = defineStore('agent', () => {
  const crud = useCrudStore<AgentConfig, CreateAgentConfigRequest>({
    initialForm: () => ({
      agentId: '',
      displayName: '',
      agentRole: 'SPECIALIST',
      systemPrompt: '',
      maxIters: 12,
      roleKeys: [],
      questionKeywords: [],
      toolGroupKeys: [],
      skillNames: [],
      mcpServerNames: [],
      llmProviderId: null,
      required: false,
      enabled: true,
      sortOrder: 0,
    }),
  })

  const runtimeStatuses = ref<Record<string, AgentRuntimeStatus>>({})
  const runtimeStartedAt = ref<Record<string, string | null>>({})
  const runtimeQuestionIds = ref<Record<string, number | null>>({})
  const lastRunAt = ref<Record<string, string | null>>({})

  const runs = ref<AgentRunRecord[]>([])
  const runsTotal = ref(0)
  const runCounts = ref<Record<string, number>>({})

  const enabledAgents = computed(() => crud.items.value.filter((a) => a.enabled))

  async function fetch() {
    await crud.fetchAll(() => api.getAll(), '无法加载 Agent 配置')
    try {
      const views = await api.listStatuses()
      const statusMap: Record<string, AgentRuntimeStatus> = {}
      const startedMap: Record<string, string | null> = {}
      const questionIdMap: Record<string, number | null> = {}
      for (const v of views) {
        statusMap[v.agentId] = v.status
        startedMap[v.agentId] = v.startedAt
        questionIdMap[v.agentId] = v.questionId ?? null
      }
      runtimeStatuses.value = statusMap
      runtimeStartedAt.value = startedMap
      runtimeQuestionIds.value = questionIdMap
    } catch {
      // ignore
    }
  }

  async function fetchRuns(agentId: string, page = 0, size = 10) {
    const pageResult: AgentPage = await api.listRuns(agentId, page, size)
    runs.value = pageResult.content
    runsTotal.value = pageResult.totalElements
    runCounts.value = { ...runCounts.value, [agentId]: pageResult.totalElements }
  }

  async function fetchRunCount(agentId: string) {
    const pageResult = await api.listRuns(agentId, 0, 1)
    runCounts.value = { ...runCounts.value, [agentId]: pageResult.totalElements }
  }

  async function fetchLastRun(agentId: string) {
    try {
      const page = await api.listRuns(agentId, 0, 1)
      if (page.content.length > 0) {
        lastRunAt.value = { ...lastRunAt.value, [agentId]: page.content[0].startedAt }
        runCounts.value = { ...runCounts.value, [agentId]: page.totalElements }
      }
    } catch {
      // ignore
    }
  }

  let currentEs: EventSource | null = null

  function subscribeStatus(agentId: string) {
    if (currentEs) {
      currentEs.close()
    }
    currentEs = api.subscribeStatus(agentId, (data) => {
      runtimeStatuses.value = {
        ...runtimeStatuses.value,
        [data.agentId]: data.status as AgentRuntimeStatus,
      }
      runtimeQuestionIds.value = {
        ...runtimeQuestionIds.value,
        [data.agentId]: data.questionId ?? null,
      }
      if (data.status === 'IDLE') {
        runtimeStartedAt.value = { ...runtimeStartedAt.value, [data.agentId]: null }
      }
    })
  }

  function unsubscribeStatus() {
    if (currentEs) {
      currentEs.close()
      currentEs = null
    }
  }

  return {
    ...crud,
    runtimeStatuses,
    runtimeStartedAt,
    runtimeQuestionIds,
    lastRunAt,
    runs,
    runsTotal,
    runCounts,
    enabledAgents,
    fetch,
    fetchRuns,
    fetchRunCount,
    fetchLastRun,
    subscribeStatus,
    unsubscribeStatus,
  }
})
