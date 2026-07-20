import { request } from './httpClient'
import type { QuestionRecord } from '../types/question'

export function getAll() {
  return request<QuestionRecord[]>('/api/questions')
}

export function get(id: number) {
  return request<QuestionRecord>(`/api/questions/${id}`)
}

export function getByConversation(conversationId: number) {
  return request<QuestionRecord[]>(`/api/questions/conversations/${conversationId}`)
}

export interface AgentEventRecord {
  id: number
  questionId: number
  attemptNo: number
  sequenceNo: number
  eventType: string
  payload: unknown
  createdAt: string
}

export function getAgentEvents(questionId: number) {
  return request<AgentEventRecord[]>(`/api/questions/${questionId}/agent-events`)
}

export function cancelStream(questionId: number) {
  return request<QuestionRecord>(`/api/questions/${questionId}/stream/cancel`, {
    method: 'POST',
  })
}

export interface LogFileBrief {
  id: number
  displayName: string
  fileSize: number
  lineCount?: number | null
  limitedMode: boolean
  parseStatus: string
}

export interface LogUploadRecord {
  id: number
  projectSpaceId: number
  originalFilename: string
  fileType: string
  fileSize: number
  status: string
  errorMessage?: string | null
  createdAt: string
  expiresAt?: string | null
  files: LogFileBrief[]
  summary?: {
    totalLines?: number
    errorCount?: number
    warnCount?: number
    firstTimestamp?: string
    lastTimestamp?: string
  } | null
}

export async function uploadLog(projectSpaceId: number, file: File): Promise<LogUploadRecord> {
  const formData = new FormData()
  formData.append('projectSpaceId', String(projectSpaceId))
  formData.append('file', file)
  const base = import.meta.env.VITE_API_BASE_URL ?? ''
  const response = await fetch(`${base}/api/log-uploads`, {
    method: 'POST',
    body: formData,
  })
  if (!response.ok) {
    const text = await response.text()
    throw new Error(`日志上传失败: ${response.status} ${text}`)
  }
  return (await response.json()) as LogUploadRecord
}

export function getLogUpload(id: number) {
  return request<LogUploadRecord>(`/api/log-uploads/${id}`)
}

/**
 * SSE 事件源信息，由后端 QuestionStreamService.appendSource 写入。
 * orchestrator 事件可能没有完整 source 字段，仅给出 agentId/agentName/depth/path。
 */
export interface EventSource {
  agentId: string
  agentName: string
  depth: number
  path: string
  agentKey?: string | null
  sessionId?: string | null
  parentSessionId?: string | null
  taskId?: string | null
}

interface StreamContentPayload extends EventSource {
  content: string
  last: boolean
  replace: boolean
  eventType: string
  messageId?: string
}

/** 工具调用事件 payload；input 来自 LLM，结构由具体工具决定。 */
export interface ToolCallPayload extends EventSource {
  id?: string
  name: string
  input?: unknown
  last: boolean
}

/** 工具结果事件 payload，可能附带 content（兜底文本）与 suspended 标志。 */
export interface ToolResultPayload extends StreamContentPayload {
  id?: string
  name?: string
  suspended?: boolean
}

export interface StatusPayload {
  status: string
  message?: string
}

export interface HandoffPayload extends StreamContentPayload {}

export interface CompletePayload {
  status: 'SUCCEEDED'
  questionId: number
  answer: string
  answerSummary?: string
  answerEvidence?: Array<{ title: string; reference: string; detail: string }>
  analysisProcess?: string
  uncertainty?: string
  nextStep?: string
  codeContext?: string
}

export interface ErrorPayload {
  status: 'FAILED'
  questionId?: number
  message: string
  errorCategory: string
  partialAnswer?: string
  analysisProcess?: string
}

export interface HeartbeatPayload {
  timestamp: number
}

/**
 * SSE 事件判别联合：通过 `type` 字段区分负载结构，让消费方的 switch 自动收窄。
 * 与后端 QuestionStreamService 中 sendEvent 的事件名一一对应。
 * 后端如新增事件类型，前端会得到未处理分支告警 —— 故意不留 catch-all。
 */
export type StreamEvent =
  | { type: 'created'; data: QuestionRecord }
  | { type: 'status'; data: StatusPayload }
  | { type: 'reasoning'; data: StreamContentPayload }
  | { type: 'hint'; data: StreamContentPayload }
  | { type: 'summary'; data: StreamContentPayload }
  | { type: 'result'; data: StreamContentPayload }
  | { type: 'handoff'; data: HandoffPayload }
  | { type: 'tool_call'; data: ToolCallPayload }
  | { type: 'tool_result'; data: ToolResultPayload }
  | { type: 'event'; data: StreamContentPayload }
  | { type: 'complete'; data: CompletePayload }
  | { type: 'error'; data: ErrorPayload }
  | { type: 'heartbeat'; data: HeartbeatPayload }

export function deleteConversation(conversationId: number) {
  return request<void>(`/api/conversations/${conversationId}`, {
    method: 'DELETE',
  })
}

const SSE_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export function stream(
  payload: {
    projectSpaceId: number
    conversationId?: number | null
    role: string
    text: string
    logUploadIds?: number[]
  },
  onEvent: (event: StreamEvent) => void
): () => void {
  const controller = new AbortController()

  fetch(`${SSE_BASE_URL}/api/questions/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
    signal: controller.signal,
  }).then(async (response) => {
    if (!response.ok) {
      const errorBody = await response.text()
      onEvent({ type: 'error', data: { status: 'FAILED', message: `请求失败: ${response.status} ${errorBody}`, errorCategory: 'http_error' } })
      return
    }

    if (!response.body) {
      onEvent({ type: 'error', data: { status: 'FAILED', message: '无法读取响应流', errorCategory: 'stream_unavailable' } })
      return
    }

    await consumeSseStream(response.body, onEvent, controller.signal)
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onEvent({ type: 'error', data: { status: 'FAILED', message: err.message, errorCategory: 'network_error' } })
    }
  })

  return () => controller.abort()
}

export function resumeStream(
  questionId: number,
  onEvent: (event: StreamEvent) => void
): () => void {
  const controller = new AbortController()

  fetch(`${SSE_BASE_URL}/api/questions/${questionId}/stream/resume`, {
    method: 'POST',
    signal: controller.signal,
  }).then(async (response) => {
    if (!response.ok) {
      const errorBody = await response.text()
      onEvent({ type: 'error', data: { status: 'FAILED', questionId, message: `请求失败: ${response.status} ${errorBody}`, errorCategory: 'http_error' } })
      return
    }

    if (!response.body) {
      onEvent({ type: 'error', data: { status: 'FAILED', questionId, message: '无法读取响应流', errorCategory: 'stream_unavailable' } })
      return
    }

    await consumeSseStream(response.body, onEvent, controller.signal)
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onEvent({ type: 'error', data: { status: 'FAILED', questionId, message: err.message, errorCategory: 'network_error' } })
    }
  })

  return () => controller.abort()
}

export function retryStream(
  questionId: number,
  onEvent: (event: StreamEvent) => void
): () => void {
  const controller = new AbortController()

  fetch(`${SSE_BASE_URL}/api/questions/${questionId}/stream/retry`, {
    method: 'POST',
    signal: controller.signal,
  }).then(async (response) => {
    if (!response.ok) {
      const errorBody = await response.text()
      onEvent({ type: 'error', data: { status: 'FAILED', questionId, message: `请求失败: ${response.status} ${errorBody}`, errorCategory: 'http_error' } })
      return
    }

    if (!response.body) {
      onEvent({ type: 'error', data: { status: 'FAILED', questionId, message: '无法读取响应流', errorCategory: 'stream_unavailable' } })
      return
    }

    await consumeSseStream(response.body, onEvent, controller.signal)
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onEvent({ type: 'error', data: { status: 'FAILED', questionId, message: err.message, errorCategory: 'network_error' } })
    }
  })

  return () => controller.abort()
}

/**
 * 标准 SSE 流消费：以空行 `\n\n` 为事件分隔，按 `event:` / `data:` 字段解析。
 * - decoder 启用 stream 模式累积多字节字符，结束时 flush
 * - buffer 跨 chunk 累积，确保跨边界的事件不丢
 */
export async function consumeSseStream(
  body: ReadableStream<Uint8Array>,
  onEvent: (event: StreamEvent) => void,
  signal: AbortSignal
): Promise<void> {
  const reader = body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buf = ''

  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) {
        buf += decoder.decode()
        flushEvents(buf, onEvent, true)
        break
      }
      buf += decoder.decode(value, { stream: true })
      buf = flushEvents(buf, onEvent, false)
    }
  } catch (err) {
    if ((err as Error).name !== 'AbortError') {
      onEvent({ type: 'error', data: { status: 'FAILED', message: (err as Error).message, errorCategory: 'stream_error' } })
    }
  }
}

/**
 * 在 buffer 中查找所有 `\n\n` 分隔的完整事件并 dispatch，返回未消费的尾部。
 * 当 final=true 时，最后残留也按一个事件 dispatch（兼容服务端未发送结束空行的情况）。
 * 导出以便单元测试。
 */
export function flushEvents(buf: string, onEvent: (event: StreamEvent) => void, final: boolean): string {
  let sep = buf.indexOf('\n\n')
  while (sep !== -1) {
    const raw = buf.slice(0, sep)
    buf = buf.slice(sep + 2)
    dispatchEvent(raw, onEvent)
    sep = buf.indexOf('\n\n')
  }
  if (final && buf.trim().length > 0) {
    dispatchEvent(buf, onEvent)
    return ''
  }
  return buf
}

/**
 * 解析单个 SSE 事件块（事件名 + data 行），data 尝试 JSON.parse 失败则回退为原始字符串。
 * 导出以便单元测试。
 */
export function dispatchEvent(raw: string, onEvent: (event: StreamEvent) => void): void {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (dataLines.length === 0) {
    return
  }
  const payload = dataLines.join('\n')
  try {
    onEvent({ type: eventName, data: JSON.parse(payload) } as unknown as StreamEvent)
  } catch {
    onEvent({ type: eventName, data: payload } as unknown as StreamEvent)
  }
}
