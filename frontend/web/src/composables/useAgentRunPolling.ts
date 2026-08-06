import { onUnmounted, ref } from 'vue'
import * as api from '../services/selfLearningApi'
import type { LearningAgentRunRecord, LearningAgentRunStatus } from '../types/selfLearning'

const TERMINAL_RUN_STATUSES: LearningAgentRunStatus[] = ['SUCCEEDED', 'PARTIAL_FAILED', 'SKIPPED', 'FAILED']
const RUN_POLL_INTERVAL_MS = 5_000
const RUN_POLL_TIMEOUT_MS = 10 * 60_000

/**
 * Self Learning Agent 后台整理运行列表与状态轮询。
 *
 * <p>提交后台整理后轮询至终态（10 分钟超时）；路由切换后旧轮询自动停止，
 * 不会用旧项目空间的请求污染新页面状态。组件卸载时清理定时器。</p>
 */
export function useAgentRunPolling() {
  const agentRuns = ref<LearningAgentRunRecord[]>([])

  let runPollTimer: ReturnType<typeof setInterval> | null = null
  let runPollStartedAt = 0
  let polledProjectSpaceId = 0

  /** 拉取指定项目空间的运行列表并写入本地状态。 */
  async function fetchRuns(projectSpaceId: number) {
    const runs = await api.listAgentRuns(projectSpaceId)
    agentRuns.value = runs
    return runs
  }

  /** 开始轮询指定项目空间的运行状态，到达终态时回调并停止。 */
  function startPolling(projectSpaceId: number, onTerminal: (runs: LearningAgentRunRecord[]) => void | Promise<void>) {
    stopPolling()
    runPollStartedAt = Date.now()
    polledProjectSpaceId = projectSpaceId
    runPollTimer = setInterval(() => pollAgentRun(onTerminal), RUN_POLL_INTERVAL_MS)
  }

  function stopPolling() {
    // 清空快照使进行中的请求回调直接返回，避免写入已失效的页面状态
    polledProjectSpaceId = 0
    if (runPollTimer) {
      clearInterval(runPollTimer)
      runPollTimer = null
    }
  }

  async function pollAgentRun(onTerminal: (runs: LearningAgentRunRecord[]) => void | Promise<void>) {
    if (Date.now() - runPollStartedAt > RUN_POLL_TIMEOUT_MS) {
      stopPolling()
      return
    }
    if (polledProjectSpaceId === 0) {
      stopPolling()
      return
    }
    try {
      const runs = await api.listAgentRuns(polledProjectSpaceId)
      if (polledProjectSpaceId === 0) {
        return
      }
      agentRuns.value = runs
      if (runs.length && TERMINAL_RUN_STATUSES.includes(runs[0].status)) {
        stopPolling()
        await onTerminal(runs)
      }
    } catch {
      stopPolling()
    }
  }

  onUnmounted(stopPolling)

  return { agentRuns, fetchRuns, startPolling, stopPolling }
}
