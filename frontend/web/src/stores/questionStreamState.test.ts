import { describe, expect, it } from 'vitest'
import type { StreamEvent } from '../services/questionApi'
import type { QuestionRecord } from '../types/question'
import {
  applyQuestionStreamEvent,
  createInitialQuestionStreamState,
  resetLiveStreamState,
  sortedStreamingAgents,
} from './questionStreamState'

describe('questionStreamState', () => {
  const context = {
    questionId: null,
    projectSpaceId: 7,
    conversationId: 13,
    role: 'developer',
    text: '入口在哪里',
  }

  it('adds created question and returns conversation side effects', () => {
    const state = createInitialQuestionStreamState()
    const questions: QuestionRecord[] = []
    const record = question({ id: 17, conversationId: 23 })

    const result = applyQuestionStreamEvent({ type: 'created', data: record }, questions, state, context)

    expect(questions).toEqual([record])
    expect(result).toEqual({
      viewingQuestionId: null,
      activeConversationId: 23,
      formConversationId: 23,
    })
  })

  it('tracks orchestrator summary and result content', () => {
    const state = createInitialQuestionStreamState()
    const questions: QuestionRecord[] = []

    applyQuestionStreamEvent(contentEvent('summary', '第一步', true), questions, state, context)
    applyQuestionStreamEvent(contentEvent('summary', '，继续', false), questions, state, context)
    applyQuestionStreamEvent(contentEvent('result', '最终答案', false), questions, state, context)

    expect(state.streamingContent).toBe('最终答案')
    expect(state.streamingAgents.orchestrator.reasoning).toBe('第一步，继续')
    expect(state.streamingAgents.orchestrator.result).toBe('最终答案')
  })

  it('tracks tool events on the source agent and global stream list', () => {
    const state = createInitialQuestionStreamState()
    const questions: QuestionRecord[] = []

    applyQuestionStreamEvent({
      type: 'tool_call',
      data: {
        agentId: 'code-researcher',
        agentName: 'Code Researcher',
        depth: 1,
        path: 'orchestrator/code-researcher',
        name: 'file_read',
        input: { path: 'src/App.vue' },
        last: false,
      },
    }, questions, state, context)
    applyQuestionStreamEvent({
      type: 'tool_result',
      data: {
        agentId: 'code-researcher',
        agentName: 'Code Researcher',
        depth: 1,
        path: 'orchestrator/code-researcher',
        name: 'file_read',
        content: 'ok',
        last: false,
        replace: false,
        eventType: 'TOOL_RESULT',
      },
    }, questions, state, context)

    expect(state.streamingToolEvents).toHaveLength(2)
    expect(state.streamingAgents['code-researcher'].toolEvents).toEqual(state.streamingToolEvents)
    expect(state.streamingAgents['code-researcher'].status).toBe('读取 file_read 结果')
  })

  it('tracks handoff events as explicit agent collaboration signals', () => {
    const state = createInitialQuestionStreamState()
    const questions: QuestionRecord[] = []

    applyQuestionStreamEvent({
      type: 'handoff',
      data: {
        agentId: 'orchestrator',
        agentName: 'Ascoder',
        depth: 0,
        path: 'orchestrator',
        content: JSON.stringify({
          fromAgentId: 'orchestrator',
          fromAgentName: 'Ascoder',
          toAgentId: 'code-researcher',
          toAgentName: 'Code Researcher',
          title: '任务委派',
          description: '父级 Agent 委派代码研究。',
        }),
        last: false,
        replace: false,
        eventType: 'HANDOFF',
      },
    }, questions, state, context)

    expect(state.streamingHandoffs).toEqual([
      expect.objectContaining({
        fromAgentId: 'orchestrator',
        toAgentId: 'code-researcher',
        title: '任务委派',
        status: 'done',
      }),
    ])
    expect(state.streamingAgents.orchestrator.status).toBe('任务委派')
    expect(state.streamingAgents['code-researcher'].agentName).toBe('Code Researcher')
  })

  it('keeps skipped handoffs out of active agent list', () => {
    const state = createInitialQuestionStreamState()
    const questions: QuestionRecord[] = []

    applyQuestionStreamEvent({
      type: 'handoff',
      data: {
        agentId: 'orchestrator',
        agentName: 'Ascoder',
        depth: 0,
        path: 'orchestrator',
        content: JSON.stringify({
          fromAgentId: 'orchestrator',
          fromAgentName: 'Ascoder',
          toAgentId: 'impact-analyzer',
          toAgentName: 'Impact Analyzer',
          title: '风险复核待命',
          description: '当前问题不需要独立影响分析。',
        }),
        last: false,
        replace: false,
        eventType: 'HANDOFF',
      },
    }, questions, state, context)

    expect(state.streamingHandoffs[0].status).toBe('skipped')
    expect(state.streamingAgents['impact-analyzer']).toBeUndefined()
    expect(sortedStreamingAgents(state.streamingAgents).map((item) => item.agentId))
      .toEqual(['orchestrator'])
  })

  it('updates matching question on complete and stops streaming', () => {
    const state = createInitialQuestionStreamState()
    state.streaming = true
    state.streamingStatus = '正在分析'
    const questions = [question({ id: 17, status: 'RUNNING' })]

    applyQuestionStreamEvent({
      type: 'complete',
      data: {
        status: 'SUCCEEDED',
        questionId: 17,
        answer: '完成',
        answerSummary: '摘要',
        answerEvidence: [{ title: '证据', reference: 'A.java:1', detail: 'detail' }],
        analysisProcess: '### Ascoder\n- done',
        uncertainty: '低',
        nextStep: '提交',
      },
    }, questions, state, context)

    expect(state.streaming).toBe(false)
    expect(state.streamingStatus).toBe('')
    expect(questions[0]).toMatchObject({
      status: 'SUCCEEDED',
      answer: '完成',
      answerSummary: '摘要',
      analysisProcess: '### Ascoder\n- done',
      uncertainty: '低',
      nextStep: '提交',
    })
    expect(questions[0].answerEvidence).toHaveLength(1)
  })

  it('records retry context and localized error on stream error', () => {
    const state = createInitialQuestionStreamState()
    state.streaming = true

    applyQuestionStreamEvent({
      type: 'error',
      data: {
        status: 'FAILED',
        errorCategory: 'tool_timeout',
        message: 'tool took too long',
      },
    }, [], state, context)

    expect(state.streaming).toBe(false)
    expect(state.lastFailedQuestion).toEqual(context)
    expect(state.error).toBe('工具调用超时：tool took too long')
  })

  it('resets live-only state without clearing error or retry context', () => {
    const state = createInitialQuestionStreamState()
    state.streamingContent = 'answer'
    state.streamingStatus = 'running'
    state.streamingToolEvents = [{ type: 'call', name: 'x', content: '{}' }]
    state.streamingHandoffs = [{
      fromAgentId: 'orchestrator',
      fromAgentName: 'Ascoder',
      toAgentId: 'code-researcher',
      toAgentName: 'Code Researcher',
      title: '任务委派',
      description: '委派',
      status: 'done',
    }]
    state.streamingAgents = {
      x: {
        agentId: 'x',
        agentName: 'X',
        depth: 0,
        path: 'x',
        status: '',
        reasoning: '',
        result: '',
        toolEvents: [],
      },
    }
    state.error = 'still here'
    state.lastFailedQuestion = context

    resetLiveStreamState(state)

    expect(state.streamingContent).toBe('')
    expect(state.streamingStatus).toBe('')
    expect(state.streamingToolEvents).toEqual([])
    expect(state.streamingHandoffs).toEqual([])
    expect(state.streamingAgents).toEqual({})
    expect(state.error).toBe('still here')
    expect(state.lastFailedQuestion).toEqual(context)
  })

  it('sorts agents by depth then display name', () => {
    const state = createInitialQuestionStreamState()
    state.streamingAgents = {
      b: agent('b', 'Beta', 1, '思考中'),
      a: agent('a', 'Alpha', 1, '思考中'),
      root: agent('root', 'Root', 0, '思考中'),
    }

    expect(sortedStreamingAgents(state.streamingAgents).map((item) => item.agentId))
      .toEqual(['root', 'a', 'b'])
  })

  it('filters agents that have not produced activity yet', () => {
    const state = createInitialQuestionStreamState()
    state.streamingAgents = {
      quiet: agent('quiet', 'Quiet', 1),
      active: agent('active', 'Active', 1, '分析中'),
    }

    expect(sortedStreamingAgents(state.streamingAgents).map((item) => item.agentId))
      .toEqual(['active'])
  })
})

function contentEvent(type: 'summary' | 'result', content: string, replace: boolean): StreamEvent {
  return {
    type,
    data: {
      agentId: 'orchestrator',
      agentName: 'Ascoder',
      depth: 0,
      path: 'orchestrator',
      content,
      replace,
      last: false,
      eventType: type,
    },
  }
}

function question(overrides: Partial<QuestionRecord>): QuestionRecord {
  return {
    id: 1,
    conversationId: null,
    conversationTitle: null,
    projectSpaceId: 7,
    projectSpaceName: 'Ascoder',
    repositoryId: 11,
    repositoryName: 'backend',
    branchWorkspaceId: null,
    branchName: 'main',
    commitSha: 'abc',
    text: '入口在哪里',
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
    createdAt: '2026-06-14T00:00:00Z',
    ...overrides,
  }
}

function agent(agentId: string, agentName: string, depth: number, status = '') {
  return {
    agentId,
    agentName,
    depth,
    path: agentId,
    status,
    reasoning: '',
    result: '',
    toolEvents: [],
  }
}
