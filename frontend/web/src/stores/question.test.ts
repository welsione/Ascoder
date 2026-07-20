import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import type { StreamEvent } from '../services/questionApi'
import type { QuestionRecord } from '../types/question'
import { useQuestionStore } from './question'

const mocks = vi.hoisted(() => ({
  getByConversation: vi.fn(),
}))

vi.mock('../utils/browserNotification', () => ({
  notifyAnswerCompleted: vi.fn(),
  requestAnswerNotificationPermission: vi.fn(),
}))

vi.mock('../services/questionApi', () => ({
  getByConversation: mocks.getByConversation,
  stream: vi.fn((_payload: unknown, onEvent: (event: StreamEvent) => void) => {
    onEvent({ type: 'created', data: question({ id: 200, conversationId: 73 }) })
    onEvent({
      type: 'complete',
      data: {
        status: 'SUCCEEDED',
        questionId: 200,
        answer: '完成回答',
        analysisProcess: '### Ascoder\n- done',
      },
    })
    return vi.fn()
  }),
}))

describe('question store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    mocks.getByConversation.mockReset()
  })

  it('finishes a newly submitted stream after pending id is replaced by real question id', () => {
    const store = useQuestionStore()
    store.form.projectSpaceId = 1
    store.form.conversationId = 73
    store.form.text = '继续分析'

    const pendingId = store.submitStream()

    expect(pendingId).toBe(-1)
    expect(store.questions).toHaveLength(1)
    expect(store.questions[0]).toMatchObject({
      id: 200,
      status: 'SUCCEEDED',
      answer: '完成回答',
    })
    expect(store.liveStateFor(200)?.streaming).toBe(false)
    expect(store.liveStateFor(pendingId!)?.streaming).toBe(false)
    expect(store.streamingQuestionId).toBe(null)
  })

  it('keeps other conversation history when refreshing one conversation', async () => {
    const store = useQuestionStore()
    store.questions = [
      question({ id: 10, conversationId: 1, text: '旧第一段' }),
      question({ id: 20, conversationId: 2, text: '另一段历史' }),
    ]
    mocks.getByConversation.mockResolvedValue([
      question({ id: 11, conversationId: 1, text: '刷新后的第一段' }),
      question({ id: 12, conversationId: 1, text: '第二轮' }),
    ])

    await store.fetchByConversation(1)

    expect(store.questions.map((q) => q.id).sort((a, b) => a - b)).toEqual([11, 12, 20])
    expect(store.currentSpaceConversationGroups.map((group) => group.conversationId).sort()).toEqual([1, 2])
    expect(store.activeConversationQuestions.map((q) => q.id)).toEqual([11, 12])
  })
})

function question(overrides: Partial<QuestionRecord>): QuestionRecord {
  return {
    id: 1,
    conversationId: null,
    conversationTitle: null,
    projectSpaceId: 1,
    projectSpaceName: 'Ascoder',
    repositoryId: 3,
    repositoryName: 'qys-private',
    branchWorkspaceId: null,
    branchName: 'main',
    commitSha: 'abc',
    text: '继续分析',
    role: 'developer',
    status: 'RUNNING',
    answer: null,
    answerSummary: null,
    answerEvidence: [],
    analysisProcess: null,
    uncertainty: null,
    nextStep: null,
    codegraphContext: null,
    logUploadIds: [],
    logUploads: [],
    queryPlan: null,
    errorMessage: null,
    startedAt: null,
    completedAt: null,
    createdAt: '2026-06-15T00:00:00Z',
    ...overrides,
  }
}
