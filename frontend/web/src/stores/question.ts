import { defineStore } from 'pinia'
import { computed, reactive, ref } from 'vue'
import * as api from '../services/questionApi'
import type { LogUploadRecord, StreamEvent } from '../services/questionApi'
import type { QuestionRecord } from '../types/question'
import {
  notifyAnswerCompleted,
  requestAnswerNotificationPermission,
} from '../utils/browserNotification'
import {
  applyQuestionStreamEvent,
  createInitialQuestionStreamState,
  resetLiveStreamState,
  sortedStreamingAgents,
} from './questionStreamState'
import type { QuestionStreamState, StreamSubmissionContext } from './questionStreamState'

export const useQuestionStore = defineStore('question', () => {
  const questions = ref<QuestionRecord[]>([])
  const activeConversationId = ref<number | null>(null)
  const viewingQuestionId = ref<number | null>(null)
  const streamingQuestionId = ref<number | null>(null)
  type QuestionLiveState = QuestionStreamState & { questionId: number | null }
  const streamStatesByQuestionId = reactive<Record<number, QuestionLiveState>>({})
  const pendingQuestionAliases = reactive<Record<number, number>>({})
  const cancelFnsByQuestionId = new Map<number, () => void>()
  let nextPendingQuestionId = -1
  const error = ref('')
  const lastFailedQuestion = ref<QuestionLiveState['lastFailedQuestion']>(null)

  const activeQuestion = computed(() => {
    if (viewingQuestionId.value) {
      return questions.value.find((q) => q.id === viewingQuestionId.value) ?? null
    }
    if (!activeConversationId.value) return null
    const convQuestions = questions.value
      .filter((q) => q.conversationId === activeConversationId.value)
      .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
    return convQuestions.length > 0 ? convQuestions[convQuestions.length - 1] : null
  })

  function latestPendingQuestionId() {
    const ids = Object.values(streamStatesByQuestionId)
      .filter((state) => state.questionId != null && state.questionId < 0 && state.streaming)
      .map((state) => state.questionId!)
    return ids.length > 0 ? Math.max(...ids) : null
  }

  const activeStreamQuestionId = computed(() => viewingQuestionId.value ?? activeQuestion.value?.id ?? streamingQuestionId.value ?? latestPendingQuestionId())
  const activeStreamState = computed(() => {
    const id = activeStreamQuestionId.value
    if (id != null && streamStatesByQuestionId[id]) {
      return streamStatesByQuestionId[id]
    }
    const pendingId = latestPendingQuestionId()
    return pendingId == null ? null : streamStatesByQuestionId[pendingId] ?? null
  })

  const streaming = computed(() =>
    Object.values(streamStatesByQuestionId).some((state) => state.streaming)
  )
  const activeStreaming = computed(() => Boolean(activeStreamState.value?.streaming))
  const streamingContent = computed(() => activeStreamState.value?.streamingContent ?? '')
  const streamingStatus = computed(() => activeStreamState.value?.streamingStatus ?? '')
  const streamingToolEvents = computed(() => activeStreamState.value?.streamingToolEvents ?? [])
  const streamingHandoffs = computed(() => activeStreamState.value?.streamingHandoffs ?? [])
  const streamingAgents = computed(() => activeStreamState.value?.streamingAgents ?? {})

  const activeConversationQuestions = computed(() =>
    activeConversationId.value == null
      ? []
      : questions.value
          .filter((q) => q.conversationId === activeConversationId.value)
          .sort((a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime())
  )

  const conversationHistory = computed(() =>
    [...questions.value].sort((a, b) => {
      const aId = a.conversationId ?? a.id
      const bId = b.conversationId ?? b.id
      if (aId !== bId) return bId - aId
      return new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
    })
  )

  interface ConversationGroup {
    conversationId: number
    title: string
    questionCount: number
    lastActiveAt: string
    questions: QuestionRecord[]
  }

  const conversationGroups = computed(() => {
    const groups = new Map<number, QuestionRecord[]>()
    for (const q of questions.value) {
      const key = q.conversationId ?? -q.id // 未分组的用负数 ID 单独成组
      const list = groups.get(key)
      if (list) {
        list.push(q)
      } else {
        groups.set(key, [q])
      }
    }
    const result: ConversationGroup[] = []
    for (const [key, groupQuestions] of groups) {
      const sorted = groupQuestions.sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
      const latest = sorted[0]
      result.push({
        conversationId: key,
        title: latest.conversationTitle || latest.text || '未命名对话',
        questionCount: sorted.length,
        lastActiveAt: latest.createdAt,
        questions: sorted,
      })
    }
    result.sort((a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime())
    return result
  })

  /** 仅展示当前项目空间的会话分组 */
  const currentSpaceConversationGroups = computed(() => {
    const spaceId = form.value.projectSpaceId
    if (spaceId == null) return []
    return conversationGroups.value.filter((group) =>
      group.questions.some((q) => q.projectSpaceId === spaceId)
    )
  })

  const streamingAgentList = computed(() =>
    sortedStreamingAgents(activeStreamState.value?.streamingAgents ?? {})
  )

  const form = ref({
    projectSpaceId: null as number | null,
    conversationId: null as number | null,
    role: 'developer',
    text: '',
    logUploadIds: [] as number[],
  })

  /** 当前已附加的日志上传元信息，用于在 Composer 中展示文件名/大小芯片 */
  const currentLogUploads = ref<LogUploadRecord[]>([])
  const uploadingLog = ref(false)

  async function attachLog(file: File) {
    if (!form.value.projectSpaceId) {
      error.value = '请先选择项目空间'
      return null
    }
    uploadingLog.value = true
    error.value = ''
    try {
      const record = await api.uploadLog(form.value.projectSpaceId, file)
      currentLogUploads.value.push(record)
      form.value.logUploadIds.push(record.id)
      return record
    } catch (err) {
      error.value = err instanceof Error ? err.message : '日志上传失败'
      return null
    } finally {
      uploadingLog.value = false
    }
  }

  function removeLogUpload(uploadId: number) {
    currentLogUploads.value = currentLogUploads.value.filter((u) => u.id !== uploadId)
    form.value.logUploadIds = form.value.logUploadIds.filter((id) => id !== uploadId)
  }

  function clearLogUpload() {
    currentLogUploads.value = []
    form.value.logUploadIds = []
  }

  function createQuestionLiveState(questionId: number): QuestionLiveState {
    return {
      questionId,
      ...createInitialQuestionStreamState(),
    }
  }

  function liveStateFor(questionId: number): QuestionLiveState | null {
    return streamStatesByQuestionId[resolveQuestionId(questionId)] ?? null
  }

  function agentListFor(questionId: number) {
    return sortedStreamingAgents(streamStatesByQuestionId[questionId]?.streamingAgents ?? {})
  }

  function ensureLiveState(questionId: number): QuestionLiveState {
    const resolvedQuestionId = resolveQuestionId(questionId)
    if (!streamStatesByQuestionId[resolvedQuestionId]) {
      streamStatesByQuestionId[resolvedQuestionId] = createQuestionLiveState(resolvedQuestionId)
    }
    return streamStatesByQuestionId[resolvedQuestionId]
  }

  function resolveQuestionId(questionId: number) {
    return pendingQuestionAliases[questionId] ?? questionId
  }

  function createPendingQuestionId() {
    return nextPendingQuestionId--
  }

  function movePendingStateToQuestion(pendingQuestionId: number, questionId: number) {
    const pendingState = streamStatesByQuestionId[pendingQuestionId]
    if (!pendingState) {
      return ensureLiveState(questionId)
    }
    const existing = ensureLiveState(questionId)
    Object.assign(existing, {
      ...pendingState,
      questionId,
    })
    delete streamStatesByQuestionId[pendingQuestionId]
    pendingQuestionAliases[pendingQuestionId] = questionId
    const pendingCancel = cancelFnsByQuestionId.get(pendingQuestionId)
    if (pendingCancel) {
      cancelFnsByQuestionId.delete(pendingQuestionId)
      cancelFnsByQuestionId.set(questionId, pendingCancel)
    }
    return existing
  }

  function startNewQuestion() {
    form.value.text = ''
    error.value = ''
  }

  function markQuestionStopped(questionId: number, message: string) {
    questions.value = questions.value.map((item) =>
      item.id === questionId ? { ...item, status: 'FAILED', errorMessage: message } : item
    )
  }

  function cancelStreaming(stopRemote = true, questionId = activeStreamQuestionId.value ?? streamingQuestionId.value) {
    if (questionId == null) {
      return
    }

    const cancelFn = cancelFnsByQuestionId.get(questionId)
    if (cancelFn) {
      cancelFn()
      cancelFnsByQuestionId.delete(questionId)
    }
    const state = streamStatesByQuestionId[questionId]
    if (state) {
      state.streaming = false
      state.streamingStatus = ''
    }
    if (streamingQuestionId.value === questionId) {
      streamingQuestionId.value = null
    }
    if (stopRemote && questionId > 0) {
      const message = '用户已停止回答'
      markQuestionStopped(questionId, message)
      api.cancelStream(questionId)
        .then((question) => {
          questions.value = questions.value.map((item) =>
            item.id === question.id ? question : item
          )
        })
        .catch((err) => {
          error.value = err instanceof Error ? err.message : '停止回答失败'
        })
    }
  }

  function cleanup() {
    for (const [questionId, cancelFn] of cancelFnsByQuestionId.entries()) {
      cancelFn()
      const state = streamStatesByQuestionId[questionId]
      if (state) {
        state.streaming = false
        state.streamingStatus = ''
      }
    }
    cancelFnsByQuestionId.clear()
    Object.keys(pendingQuestionAliases).forEach((key) => delete pendingQuestionAliases[Number(key)])
    error.value = ''
  }

  function startNewConversation() {
    cleanup()
    startNewQuestion()
    activeConversationId.value = null
    viewingQuestionId.value = null
    form.value.conversationId = null
    // 保留 projectSpaceId，让用户可以继续在同一空间提问
  }

  function enterConversation(conversationId: number) {
    viewingQuestionId.value = null
    // 负数 ID 表示未分组的问题，不设置 conversationId 上下文
    if (conversationId > 0) {
      activeConversationId.value = conversationId
      form.value.conversationId = conversationId
      // 从对话中的问题推导 projectSpaceId
      const convQuestion = questions.value.find((q) => q.conversationId === conversationId)
      if (convQuestion?.projectSpaceId != null) {
        form.value.projectSpaceId = convQuestion.projectSpaceId
      }
    } else {
      activeConversationId.value = null
      form.value.conversationId = null
    }
    error.value = ''
  }

  async function fetch() {
    try {
      questions.value = await api.getAll()
    } catch (err) {
      error.value = err instanceof Error ? err.message : '无法加载问题列表'
    }
  }

  async function fetchByConversation(conversationId: number) {
    try {
      const result = await api.getByConversation(conversationId)
      questions.value = [
        ...questions.value.filter((q) => q.conversationId !== conversationId),
        ...result,
      ]
      activeConversationId.value = conversationId
      viewingQuestionId.value = null
      form.value.conversationId = conversationId
      const convQuestion = result.find((q) => q.projectSpaceId != null)
      if (convQuestion?.projectSpaceId != null) {
        form.value.projectSpaceId = convQuestion.projectSpaceId
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '无法加载对话问题'
    }
  }

  async function openQuestionThread(questionId: number) {
    await get(questionId)
    const question = questions.value.find((q) => q.id === questionId)
    if (!question) {
      return null
    }
    if (question.conversationId != null) {
      await fetchByConversation(question.conversationId)
      const refreshed = questions.value.find((q) => q.id === questionId) ?? question
      if (refreshed.status === 'SUCCEEDED' || refreshed.status === 'FAILED') {
        replayAgentEvents(questionId, refreshed)
      }
      return refreshed
    }
    await select(questionId)
    return question
  }

  /**
   * 流式提交问题，通过 SSE 实时接收 Agent 回答事件。
   */
  function submitStream(): number | null {
    if (!form.value.projectSpaceId || !form.value.text.trim()) return null

    requestAnswerNotificationPermission()

    const pendingQuestionId = createPendingQuestionId()
    const state = ensureLiveState(pendingQuestionId)
    state.streaming = true
    error.value = ''
    lastFailedQuestion.value = null

    const questionText = form.value.text.trim()
    const currentProjectSpaceId = form.value.projectSpaceId
    const currentConversationId = form.value.conversationId
    const currentLogUploadIds = [...form.value.logUploadIds]
    const currentRole = form.value.role
    form.value.text = ''
    clearLogUpload()

    const cancel = api.stream(
      {
        projectSpaceId: currentProjectSpaceId,
        conversationId: currentConversationId,
        role: currentRole,
        text: questionText,
        logUploadIds: currentLogUploadIds,
      },
      (event: StreamEvent) => applyStreamEvent(event, pendingQuestionId, {
        projectSpaceId: currentProjectSpaceId,
        conversationId: currentConversationId,
        role: currentRole,
        text: questionText,
      })
    )

    const wrappedCancel = () => {
      cancel()
      state.streaming = false
      state.streamingStatus = ''
      cancelFnsByQuestionId.delete(pendingQuestionId)
    }
    cancelFnsByQuestionId.set(pendingQuestionId, wrappedCancel)

    return pendingQuestionId
  }

  function resumeQuestion(questionId: number, retry = false): (() => void) | null {
    const question = questions.value.find((q) => q.id === questionId)
    if (!question?.projectSpaceId) return null

    requestAnswerNotificationPermission()
    const state = ensureLiveState(questionId)
    state.streaming = true
    streamingQuestionId.value = questionId
    resetLiveStreamState(state)
    error.value = ''
    lastFailedQuestion.value = null
    if (question.conversationId != null) {
      activeConversationId.value = question.conversationId
      form.value.conversationId = question.conversationId
    }
    form.value.projectSpaceId = question.projectSpaceId
    questions.value = questions.value.map((item) =>
      item.id === questionId ? { ...item, status: 'RUNNING', errorMessage: null } : item
    )

    const streamFn = retry ? api.retryStream : api.resumeStream
    const cancel = streamFn(questionId, (event: StreamEvent) => applyStreamEvent(event, questionId, {
      projectSpaceId: question.projectSpaceId!,
      conversationId: question.conversationId,
      role: question.role ?? form.value.role,
      text: question.text,
    }))

    const wrappedCancel = () => {
      cancel()
      state.streaming = false
      state.streamingStatus = ''
      cancelFnsByQuestionId.delete(questionId)
    }
    cancelFnsByQuestionId.set(questionId, wrappedCancel)

    return wrappedCancel
  }

  async function resumeRunningForSpace(projectSpaceId: number): Promise<(() => void) | null> {
    void projectSpaceId
    return null
  }

  function applyStreamEvent(event: StreamEvent, questionId: number, context: StreamSubmissionContext) {
    let state: QuestionLiveState = ensureLiveState(questionId)
    if (event.type === 'created') {
      streamingQuestionId.value = event.data.id
      if (questionId < 0) {
        state = movePendingStateToQuestion(questionId, event.data.id)
      } else {
        state = ensureLiveState(event.data.id)
      }
    }
    const result = applyQuestionStreamEvent(event, questions.value, state, context)
    if (state.lastFailedQuestion) {
      lastFailedQuestion.value = state.lastFailedQuestion
    }
    if (state.error) {
      error.value = state.error
    }
    if ('viewingQuestionId' in result && viewingQuestionId.value == null) {
      viewingQuestionId.value = result.viewingQuestionId ?? null
    }
    if (result.activeConversationId != null && viewingQuestionId.value == null) {
      activeConversationId.value = result.activeConversationId
    }
    if (result.formConversationId != null) {
      form.value.conversationId = result.formConversationId
    }
    if (event.type === 'complete' || event.type === 'error') {
      const completedQuestionId = event.type === 'complete' ? event.data.questionId : event.data.questionId
      const doneQuestionId = completedQuestionId ?? questionId
      if (doneQuestionId != null) {
        cancelFnsByQuestionId.delete(doneQuestionId)
      }
      cancelFnsByQuestionId.delete(questionId)
      if (streamingQuestionId.value === completedQuestionId) {
        streamingQuestionId.value = null
      }
    }
    if (event.type === 'complete') {
      notifyAnswerCompleted(context.text)
    }
  }

  function retryLastFailed(): (() => void) | null {
    if (!lastFailedQuestion.value) return null
    const failed = lastFailedQuestion.value
    if (failed.questionId != null) {
      return retryQuestion(failed.questionId)
    }
    form.value.projectSpaceId = failed.projectSpaceId
    form.value.conversationId = failed.conversationId
    form.value.role = failed.role
    form.value.text = failed.text
    const submittedId = submitStream()
    return submittedId == null ? null : cancelFnsByQuestionId.get(submittedId) ?? null
  }

  function retryQuestion(questionId: number): (() => void) | null {
    return resumeQuestion(questionId, true)
  }

  async function select(questionId: number) {
    const question = questions.value.find((q) => q.id === questionId)
    if (question) {
      viewingQuestionId.value = questionId
      if (question.conversationId != null) {
        activeConversationId.value = question.conversationId
        form.value.conversationId = question.conversationId
      } else {
        activeConversationId.value = null
        form.value.conversationId = null
      }
      if (question.projectSpaceId != null) {
        form.value.projectSpaceId = question.projectSpaceId
      }
      error.value = ''

      // 对已完成/失败的问题回放 Agent 事件，构建 live state 供 AnalysisTimeline 使用
      if (question.status === 'SUCCEEDED' || question.status === 'FAILED') {
        replayAgentEvents(questionId, question)
      }
    }
  }

  /**
   * 从后端拉取 Agent 事件并回放到 streamStatesByQuestionId，使历史问题的分析过程可交互展示。
   */
  async function replayAgentEvents(questionId: number, question: QuestionRecord) {
    // 已有 live state 且非空则跳过
    const existing = streamStatesByQuestionId[questionId]
    if (existing && (existing.streamingAgents && Object.keys(existing.streamingAgents).length > 0)) {
      return
    }

    try {
      const events = await api.getAgentEvents(questionId)
      if (!events.length) return

      const state = ensureLiveState(questionId)
      resetLiveStreamState(state)
      state.streaming = false

      const context: StreamSubmissionContext = {
        projectSpaceId: question.projectSpaceId ?? 0,
        conversationId: question.conversationId ?? null,
        role: question.role ?? form.value.role,
        text: question.text,
      }

      for (const event of events) {
        if (event.eventType === 'created' || event.eventType === 'complete' || event.eventType === 'error') {
          continue
        }
        const streamEvent = { type: event.eventType, data: event.payload } as StreamEvent
        applyQuestionStreamEvent(streamEvent, questions.value, state, context)
      }
    } catch {
      // 回放失败不影响查看
    }
  }

  async function get(id: number) {
    try {
      const result = await api.get(id)
      const idx = questions.value.findIndex((q) => q.id === id)
      if (idx !== -1) {
        questions.value[idx] = result
      } else {
        questions.value.push(result)
      }
    } catch (err) {
      error.value = err instanceof Error ? err.message : '无法加载问题'
    }
  }

  /** 搜索当前对话中的消息 */
  function searchMessages(keyword: string) {
    if (!keyword.trim()) return []
    const lower = keyword.toLowerCase()
    return questions.value.filter(q =>
      q.text?.toLowerCase().includes(lower) ||
      q.answer?.toLowerCase().includes(lower)
    )
  }

  /** 删除会话及其关联问题 */
  async function deleteConversation(conversationId: number) {
    await api.deleteConversation(conversationId)
    questions.value = questions.value.filter(q => q.conversationId !== conversationId)
    if (activeConversationId.value === conversationId) {
      activeConversationId.value = null
      form.value.conversationId = null
    }
  }

  /** 导出当前对话为 Markdown 文本 */
  function exportConversation(): string {
    const convQuestions = activeConversationQuestions.value
    if (convQuestions.length === 0) return ''

    const lines: string[] = []
    lines.push(`# ${convQuestions[0]?.conversationTitle || '对话记录'}`)
    lines.push('')
    lines.push(`导出时间：${new Date().toLocaleString('zh-CN')}`)
    lines.push('')

    convQuestions.forEach((q, i) => {
      lines.push(`## 第 ${i + 1} 轮`)
      lines.push('')
      lines.push(`**问题**：${q.text}`)
      lines.push('')
      if (q.answer) {
        lines.push(q.answer)
        lines.push('')
      }
      if (q.answerSummary) {
        lines.push(`> 摘要：${q.answerSummary}`)
        lines.push('')
      }
      lines.push('---')
      lines.push('')
    })

    return lines.join('\n')
  }

  return {
    questions,
    activeConversationId,
    viewingQuestionId,
    streamingQuestionId,
    activeQuestion,
    activeConversationQuestions,
    conversationHistory,
    conversationGroups,
    currentSpaceConversationGroups,
    streamStatesByQuestionId,
    activeStreamState,
    activeStreamQuestionId,
    activeStreaming,
    streaming,
    streamingContent,
    streamingStatus,
    streamingToolEvents,
    streamingHandoffs,
    streamingAgents,
    streamingAgentList,
    error,
    form,
    currentLogUploads,
    uploadingLog,
    lastFailedQuestion,
    liveStateFor,
    agentListFor,
    startNewQuestion,
    startNewConversation,
    enterConversation,
    cancelStreaming,
    cleanup,
    retryLastFailed,
    retryQuestion,
    resumeQuestion,
    resumeRunningForSpace,
    fetch,
    fetchByConversation,
    openQuestionThread,
    submitStream,
    select,
    get,
    deleteConversation,
    searchMessages,
    exportConversation,
    attachLog,
    removeLogUpload,
    clearLogUpload,
  }
})
