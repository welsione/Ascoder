import type { StreamEvent } from '../services/questionApi'
import type { QuestionRecord } from '../types/question'

export interface StreamingToolEvent {
  type: 'call' | 'result'
  name: string
  content: string
}

export interface StreamingAgentState {
  agentId: string
  agentName: string
  depth: number
  path: string
  status: string
  reasoning: string
  result: string
  toolEvents: StreamingToolEvent[]
}

export interface StreamingHandoffEvent {
  fromAgentId: string
  fromAgentName: string
  toAgentId: string
  toAgentName: string
  title: string
  description: string
  status: 'active' | 'done' | 'waiting' | 'skipped'
}

export interface LastFailedQuestion {
  questionId: number | null
  projectSpaceId: number
  conversationId: number | null
  role: string
  text: string
}

export interface QuestionStreamState {
  streaming: boolean
  streamingContent: string
  streamingStatus: string
  streamingToolEvents: StreamingToolEvent[]
  streamingHandoffs: StreamingHandoffEvent[]
  streamingAgents: Record<string, StreamingAgentState>
  error: string
  lastFailedQuestion: LastFailedQuestion | null
}

export interface StreamSubmissionContext {
  projectSpaceId: number
  conversationId: number | null
  role: string
  text: string
}

export interface StreamEventApplyResult {
  activeConversationId?: number
  formConversationId?: number
  viewingQuestionId?: number | null
}

type AgentSourcePayload = {
  agentId?: string
  agentName?: string
  depth?: number
  path?: string
}

const ERROR_CATEGORY_LABELS: Record<string, string> = {
  max_iters_exhausted: '达到推理轮次上限',
  model_timeout: '模型响应超时',
  tool_timeout: '工具调用超时',
  codegraph_repository_error: 'CodeGraph 仓库解析失败',
  codegraph_index_missing: 'CodeGraph 索引缺失',
  summary_timeout: '回答汇总超时',
  interrupted: '任务已中断',
  timeout: '请求超时',
  agent_error: 'Agent 执行错误',
  http_error: '请求失败',
  stream_unavailable: '流式响应不可用',
  network_error: '网络错误',
  stream_error: '流式响应错误',
  unknown: '未知错误',
}

export function createInitialQuestionStreamState(): QuestionStreamState {
  return {
    streaming: false,
    streamingContent: '',
    streamingStatus: '',
    streamingToolEvents: [],
    streamingHandoffs: [],
    streamingAgents: {},
    error: '',
    lastFailedQuestion: null,
  }
}

export function resetLiveStreamState(state: QuestionStreamState) {
  state.streamingContent = ''
  state.streamingStatus = ''
  state.streamingToolEvents = []
  state.streamingHandoffs = []
  state.streamingAgents = {}
}

export function sortedStreamingAgents(agents: Record<string, StreamingAgentState>) {
  return Object.values(agents).filter(agentHasActivity).sort((a, b) => {
    if (a.depth !== b.depth) return a.depth - b.depth
    return a.agentName.localeCompare(b.agentName)
  })
}

function agentHasActivity(agent: StreamingAgentState) {
  return Boolean(agent.status || agent.reasoning || agent.result || agent.toolEvents.length)
}

export function applyQuestionStreamEvent(
  event: StreamEvent,
  questions: QuestionRecord[],
  state: QuestionStreamState,
  context: StreamSubmissionContext
): StreamEventApplyResult {
  switch (event.type) {
    case 'created':
      return applyCreated(event.data, questions, state)
    case 'status':
      return applyStatus(event.data, state)
    case 'reasoning':
      applyStreamContent('reasoning', event.data, state)
      return {}
    case 'hint':
      applyStreamContent('hint', event.data, state)
      return {}
    case 'tool_call':
      applyToolCall(event.data, state)
      return {}
    case 'tool_result':
      applyToolResult(event.data, state)
      return {}
    case 'summary':
      applyStreamContent('summary', event.data, state)
      return {}
    case 'result':
      applyStreamContent('result', event.data, state)
      return {}
    case 'handoff':
      applyHandoff(event.data, state)
      return {}
    case 'complete':
      applyComplete(event.data, questions, state)
      return {}
    case 'error':
      applyError(event.data, questions, state, context)
      return {}
    case 'heartbeat':
      return {}
    default:
      return {}
  }
}

function applyCreated(record: QuestionRecord, questions: QuestionRecord[], state: QuestionStreamState): StreamEventApplyResult {
  const idx = questions.findIndex((q) => q.id === record.id)
  if (idx === -1) {
    questions.push(record)
  } else {
    questions[idx] = record
  }
  if (record.status === 'RUNNING' && record.answer && !state.streamingContent) {
    state.streamingContent = record.answer
  }
  if (!record.conversationId) {
    return { viewingQuestionId: null }
  }
  return {
    viewingQuestionId: null,
    activeConversationId: record.conversationId,
    formConversationId: record.conversationId,
  }
}

function applyStatus(data: { status?: string; message?: string }, state: QuestionStreamState): StreamEventApplyResult {
  state.streamingStatus = data.message || data.status || ''
  agentStateFrom(null, state).status = state.streamingStatus
  return {}
}

function applyStreamContent(
  eventType: 'reasoning' | 'hint' | 'summary' | 'result' | 'event',
  data: { content?: string; replace?: boolean } & AgentSourcePayload,
  state: QuestionStreamState
) {
  if (!data.content) return
  const agent = agentStateFrom(data, state)
  if (eventType === 'result') {
    agent.result = data.content
    agent.status = '形成结论'
    if (agent.agentId === 'orchestrator') {
      state.streamingContent = data.content
    }
    return
  }

  agent.status = streamStatusLabel(eventType)

  if (data.replace) {
    agent.reasoning = data.content
  } else {
    agent.reasoning += data.content
  }

  if (eventType === 'summary' && agent.agentId === 'orchestrator') {
    if (data.replace) {
      state.streamingContent = data.content
    } else {
      state.streamingContent += data.content
    }
  }
}

function streamStatusLabel(eventType: 'reasoning' | 'hint' | 'summary' | 'result' | 'event') {
  if (eventType === 'reasoning') return '思考中'
  if (eventType === 'hint') return '分析中'
  if (eventType === 'summary') return '整理阶段摘要'
  if (eventType === 'event') return '处理中'
  return '进行中'
}

function applyToolCall(data: { name?: string; input?: unknown } & AgentSourcePayload, state: QuestionStreamState) {
  if (!data.name) return
  const toolEvent: StreamingToolEvent = {
    type: 'call',
    name: data.name,
    content: data.input ? JSON.stringify(data.input) : '',
  }
  const agent = agentStateFrom(data, state)
  agent.toolEvents.push(toolEvent)
  agent.status = `调用 ${data.name}`
  state.streamingToolEvents.push(toolEvent)
}

function applyToolResult(data: { name?: string; content?: string } & AgentSourcePayload, state: QuestionStreamState) {
  if (!data.name && !data.content) return
  const toolEvent: StreamingToolEvent = {
    type: 'result',
    name: data.name ?? 'tool',
    content: data.content ?? '',
  }
  const agent = agentStateFrom(data, state)
  agent.toolEvents.push(toolEvent)
  agent.status = `读取 ${toolEvent.name} 结果`
  state.streamingToolEvents.push(toolEvent)
}

function applyHandoff(data: { content?: string } & AgentSourcePayload, state: QuestionStreamState) {
  if (!data.content) return
  const handoff = parseHandoffContent(data.content)
  if (!handoff) return
  state.streamingHandoffs.push(handoff)
  agentStateFrom({
    agentId: handoff.fromAgentId,
    agentName: handoff.fromAgentName,
    depth: data.depth,
    path: data.path,
  }, state).status = handoff.title
  if (handoff.toAgentId !== 'final-answer' && handoff.status !== 'skipped') {
    agentStateFrom({
      agentId: handoff.toAgentId,
      agentName: handoff.toAgentName,
      depth: handoff.toAgentId === 'orchestrator' ? 0 : 1,
      path: handoff.toAgentId === 'orchestrator' ? 'orchestrator' : `orchestrator/${handoff.toAgentId}`,
    }, state)
  }
}

function parseHandoffContent(content: string): StreamingHandoffEvent | null {
  try {
    const parsed = JSON.parse(content) as Partial<StreamingHandoffEvent>
    if (!parsed.fromAgentId || !parsed.fromAgentName || !parsed.toAgentId || !parsed.toAgentName || !parsed.title) {
      return null
    }
    return {
      fromAgentId: parsed.fromAgentId,
      fromAgentName: parsed.fromAgentName,
      toAgentId: parsed.toAgentId,
      toAgentName: parsed.toAgentName,
      title: parsed.title,
      description: parsed.description ?? '',
      status: handoffStatus(parsed.title),
    }
  } catch {
    return null
  }
}

function handoffStatus(title: string): StreamingHandoffEvent['status'] {
  if (title.includes('待命')) return 'skipped'
  return 'done'
}

function applyComplete(
  completed: Extract<StreamEvent, { type: 'complete' }>['data'],
  questions: QuestionRecord[],
  state: QuestionStreamState
) {
  const idx = questions.findIndex((q) => q.id === completed.questionId)
  if (idx !== -1) {
    questions[idx] = {
      ...questions[idx],
      status: 'SUCCEEDED',
      answer: completed.answer,
      answerSummary: completed.answerSummary ?? null,
      answerEvidence: completed.answerEvidence ?? [],
      analysisProcess: completed.analysisProcess ?? null,
      uncertainty: completed.uncertainty ?? null,
      nextStep: completed.nextStep ?? null,
    }
  }
  state.streaming = false
  state.streamingStatus = ''
}

function applyError(
  data: { questionId?: number; message?: string; errorCategory?: string; partialAnswer?: string; analysisProcess?: string },
  questions: QuestionRecord[],
  state: QuestionStreamState,
  context: StreamSubmissionContext
) {
  state.lastFailedQuestion = {
    questionId: data.questionId ?? null,
    projectSpaceId: context.projectSpaceId,
    conversationId: context.conversationId,
    role: context.role,
    text: context.text,
  }
  if (data.message) {
    const category = data.errorCategory ?? 'agent_error'
    state.error = `${ERROR_CATEGORY_LABELS[category] ?? category}：${data.message}`
  }
  if (data.questionId != null) {
    const idx = questions.findIndex((q) => q.id === data.questionId)
    if (idx !== -1) {
      questions[idx] = {
        ...questions[idx],
        status: 'FAILED',
        answer: data.partialAnswer ?? questions[idx].answer,
        analysisProcess: data.analysisProcess ?? questions[idx].analysisProcess,
        errorMessage: data.message ?? questions[idx].errorMessage,
      }
    }
  }
  state.streaming = false
  state.streamingStatus = ''
}

function agentStateFrom(source: AgentSourcePayload | null, state: QuestionStreamState) {
  const agentId = source?.agentId ?? 'orchestrator'
  if (!state.streamingAgents[agentId]) {
    state.streamingAgents[agentId] = {
      agentId,
      agentName: source?.agentName ?? agentLabel(agentId),
      depth: source?.depth ?? 0,
      path: source?.path ?? agentId,
      status: '',
      reasoning: '',
      result: '',
      toolEvents: [],
    }
  }
  return state.streamingAgents[agentId]
}

function agentLabel(agentId: string) {
  if (agentId === 'orchestrator') return 'Ascoder'
  if (agentId === 'code-researcher') return 'Code Researcher'
  if (agentId === 'impact-analyzer') return 'Impact Analyzer'
  if (agentId === 'product-manager') return 'Product Manager Agent'
  if (agentId === 'test-manager') return 'Test Manager Agent'
  return agentId
}
