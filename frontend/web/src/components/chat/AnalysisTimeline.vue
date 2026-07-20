<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { ArrowRight, CheckCircle2, ChevronDown, CircleDashed, ClipboardCheck, FileSearch, GitBranch, MessageSquareText, Sparkles, Wrench } from 'lucide-vue-next'
import { renderMarkdown, renderMermaidBlocks } from '../../utils/markdown'

type ToolEvent = {
  type: 'call' | 'result'
  name: string
  content: string
}

type AgentStep = {
  agentId: string
  agentName: string
  depth: number
  path: string
  status: string
  reasoning: string
  result: string
  toolEvents: ToolEvent[]
}

type TimelineStep = {
  id: string
  title: string
  subtitle: string
  status: string
  reasoning: string
  result: string
  toolEvents: ToolEvent[]
}

type CollaborationNode = {
  id: string
  name: string
  role: string
  description: string
  status: 'active' | 'done' | 'waiting' | 'skipped'
  metrics: string
}

type CollaborationHandoff = {
  id: string
  from: string
  to: string
  title: string
  description: string
  status: 'active' | 'done' | 'waiting' | 'skipped'
}

type HandoffEvent = {
  fromAgentId: string
  fromAgentName: string
  toAgentId: string
  toAgentName: string
  title: string
  description: string
  status: 'active' | 'done' | 'waiting' | 'skipped'
}

const props = withDefaults(defineProps<{
  agents?: AgentStep[]
  handoffs?: HandoffEvent[]
  markdown?: string | null
  live?: boolean
}>(), {
  agents: () => [],
  handoffs: () => [],
  markdown: null,
  live: false,
})

const rootRef = ref<HTMLElement | null>(null)

const steps = computed<TimelineStep[]>(() => {
  const savedSteps = parseMarkdownSteps(props.markdown)
  if (props.agents.length) {
    const liveSteps = props.agents.map((agent, index) => ({
      id: agent.agentId || `agent-${index}`,
      title: agent.agentName || agent.agentId || `步骤 ${index + 1}`,
      subtitle: agent.path || `Step ${index + 1}`,
      status: agent.status,
      reasoning: agent.reasoning,
      result: agent.result,
      toolEvents: agent.toolEvents,
    }))
    return mergeSteps(savedSteps, liveSteps).filter(stepHasActivity)
  }
  return savedSteps.filter(stepHasActivity)
})

const eventCount = computed(() =>
  steps.value.reduce(
    (total, step) => total
      + (step.reasoning ? 1 : 0)
      + (step.result ? 1 : 0)
      + step.toolEvents.length,
    0
  )
)

const currentActivity = computed(() => {
  const active = [...steps.value].reverse().find((step) =>
    step.status || step.reasoning || step.result || step.toolEvents.length
  )
  if (!active) return props.live ? '分析中...' : '查看分析过程'
  const latestTool = active.toolEvents[active.toolEvents.length - 1]
  if (latestTool) {
    return `${active.title} ${latestTool.type === 'call' ? '调用工具' : '读取结果'}：${latestTool.name}`
  }
  if (active.status) return `${active.title} ${active.status}...`
  if (active.result) return `${active.title} 形成结论...`
  return `${active.title} 思考中...`
})

const agentMap = computed(() => {
  const map = new Map<string, TimelineStep>()
  for (const step of steps.value) {
    map.set(step.id, step)
  }
  return map
})

const savedHandoffs = computed(() => parseHandoffMarkdown(props.markdown))
const effectiveHandoffs = computed(() => mergeHandoffs(savedHandoffs.value, props.handoffs))

const collaborationNodes = computed<CollaborationNode[]>(() => {
  const running = props.live

  return steps.value.map((step) => ({
    id: step.id,
    name: step.title,
    role: agentRole(step.id),
    description: agentDescription(step.id),
    status: nodeStatus(step, running),
    metrics: `${step.toolEvents.length} 个工具事件`,
  }))
})

const collaborationHandoffs = computed<CollaborationHandoff[]>(() => {
  return effectiveHandoffs.value
    .filter((handoff) => handoff.status !== 'skipped')
    .map((handoff, index) => ({
      id: `handoff-${index}-${handoff.fromAgentId}-${handoff.toAgentId}`,
      from: handoff.fromAgentName,
      to: handoff.toAgentName,
      title: handoff.title,
      description: handoff.description,
      status: handoff.status,
    }))
})

watch(
  steps,
  () => nextTick(() => {
    if (rootRef.value) renderMermaidBlocks(rootRef.value)
  }),
  { immediate: true }
)

function parseMarkdownSteps(markdown: string | null | undefined): TimelineStep[] {
  if (!markdown?.trim()) return []

  const parts = splitAgentMarkdown(markdown)

  if (!parts.length) {
    return [markdownStep('analysis-0', '分析过程', 'Saved process', markdown)]
  }

  return parts.map(({ title, body }, index) => {
    return markdownStep(
      agentIdFromTitle(title) || `analysis-${index}`,
      agentTitle(title) || `步骤 ${index + 1}`,
      `Step ${index + 1}`,
      body
    )
  })
}

function splitAgentMarkdown(markdown: string) {
  const lines = markdown.split(/\r?\n/)
  const parts: Array<{ title: string; body: string }> = []
  let currentTitle = ''
  let currentBody: string[] = []

  for (const line of lines) {
    const match = line.match(/^###\s+(.+?)\s*$/)
    if (match && agentIdFromTitle(match[1])) {
      if (currentTitle) {
        parts.push({ title: currentTitle, body: currentBody.join('\n').trim() })
      }
      currentTitle = match[1].trim()
      currentBody = []
    } else if (currentTitle) {
      currentBody.push(line)
    }
  }

  if (currentTitle) {
    parts.push({ title: currentTitle, body: currentBody.join('\n').trim() })
  }

  return parts.filter((part) => part.body)
}

function agentIdFromTitle(title: string) {
  const value = title.trim().toLowerCase()
  if (value === 'ascoder' || value.includes('orchestrator') || value.includes('汇总')) return 'orchestrator'
  if (value === 'code-researcher' || value.includes('code researcher')) return 'code-researcher'
  if (value === 'impact-analyzer' || value.includes('impact analyzer')) return 'impact-analyzer'
  if (value === 'product-manager' || value.includes('product manager') || value.includes('产品经理')) return 'product-manager'
  if (value === 'test-manager' || value.includes('test manager') || value.includes('测试经理')) return 'test-manager'
  return ''
}

function agentTitle(title: string) {
  const id = agentIdFromTitle(title)
  if (id === 'orchestrator') return 'Ascoder'
  if (id === 'code-researcher') return 'Code Researcher'
  if (id === 'impact-analyzer') return 'Impact Analyzer'
  if (id === 'product-manager') return 'Product Manager Agent'
  if (id === 'test-manager') return 'Test Manager Agent'
  return title
}

function agentRole(agentId: string) {
  if (agentId === 'orchestrator') return '父级编排'
  if (agentId === 'code-researcher') return '代码研究'
  if (agentId === 'impact-analyzer') return '影响分析'
  if (agentId === 'product-manager') return '产品经理'
  if (agentId === 'test-manager') return '测试经理'
  return 'Agent'
}

function agentDescription(agentId: string) {
  if (agentId === 'orchestrator') return '拆解问题、协调专家，并组织最终答案。'
  if (agentId === 'code-researcher') return '检索文件、符号、调用链和代码证据。'
  if (agentId === 'impact-analyzer') return '评估改动影响、回归风险和验证范围。'
  if (agentId === 'product-manager') return '用客户可理解的人话解释业务逻辑，并评估新需求是否合理。'
  if (agentId === 'test-manager') return '拆解测试点、测试用例和自动化测试建议。'
  return '处理当前问题中的专项任务。'
}

function markdownStep(id: string, title: string, subtitle: string, body: string): TimelineStep {
  const toolEvents = parseToolEvents(body)
  const parsed = splitResult(body)
  return {
    id,
    title,
    subtitle,
    status: '已完成',
    reasoning: parsed.reasoning,
    result: parsed.result,
    toolEvents,
  }
}

function mergeSteps(savedSteps: TimelineStep[], liveSteps: TimelineStep[]) {
  const merged = new Map<string, TimelineStep>()
  for (const step of savedSteps) {
    merged.set(step.id, step)
  }
  for (const step of liveSteps) {
    const saved = merged.get(step.id)
    merged.set(step.id, saved ? mergeStep(saved, step) : step)
  }
  return Array.from(merged.values())
}

function mergeStep(saved: TimelineStep, live: TimelineStep): TimelineStep {
  return {
    ...live,
    status: live.status || saved.status,
    reasoning: live.reasoning || saved.reasoning,
    result: live.result || saved.result,
    toolEvents: live.toolEvents.length ? live.toolEvents : saved.toolEvents,
  }
}

function stepHasActivity(step: TimelineStep) {
  return Boolean(
    step.status
    || step.reasoning.trim()
    || step.result.trim()
    || step.toolEvents.length
  )
}

function splitResult(body: string) {
  const marker = 'Agent 结论：'
  const index = body.lastIndexOf(marker)
  if (index < 0) {
    return { reasoning: body, result: '' }
  }
  return {
    reasoning: body.slice(0, index).trim(),
    result: body.slice(index + marker.length).trim(),
  }
}

function parseToolEvents(body: string): ToolEvent[] {
  const events: ToolEvent[] = []
  const pattern = /(工具调用|工具结果)\s+`([^`]+)`/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(body)) !== null) {
    events.push({
      type: match[1] === '工具调用' ? 'call' : 'result',
      name: match[2],
      content: '',
    })
  }
  return events
}

function parseHandoffMarkdown(markdown: string | null | undefined): HandoffEvent[] {
  if (!markdown?.trim()) return []
  const events: HandoffEvent[] = []
  const pattern = /Agent 交接：(\{[^\n]+\})/g
  let match: RegExpExecArray | null
  while ((match = pattern.exec(markdown)) !== null) {
    try {
      const parsed = JSON.parse(match[1]) as Partial<HandoffEvent>
      if (!parsed.fromAgentId || !parsed.fromAgentName || !parsed.toAgentId || !parsed.toAgentName || !parsed.title) {
        continue
      }
      events.push({
        fromAgentId: parsed.fromAgentId,
        fromAgentName: parsed.fromAgentName,
        toAgentId: parsed.toAgentId,
        toAgentName: parsed.toAgentName,
        title: parsed.title,
        description: parsed.description ?? '',
        status: parsed.title.includes('待命') ? 'skipped' : 'done',
      })
    } catch {
      // 忽略历史中无法解析的交接片段。
    }
  }
  return events
}

function mergeHandoffs(saved: HandoffEvent[], live: HandoffEvent[]) {
  const seen = new Set<string>()
  const merged: HandoffEvent[] = []
  for (const handoff of [...saved, ...live]) {
    const key = `${handoff.fromAgentId}:${handoff.toAgentId}:${handoff.title}`
    if (seen.has(key)) {
      continue
    }
    seen.add(key)
    merged.push(handoff)
  }
  return merged
}

function stepState(index: number) {
  if (!props.live) return 'done'
  const step = steps.value[index]
  if (step?.result || step?.status === '已完成') return 'done'
  return index === steps.value.length - 1 ? 'active' : 'done'
}

function nodeStatus(step: TimelineStep, live: boolean): CollaborationNode['status'] {
  if (step.result || step.status === '已完成' || !live) return 'done'
  return 'active'
}

function statusText(status: CollaborationNode['status']) {
  if (status === 'done') return '已完成'
  if (status === 'active') return '协作中'
  if (status === 'skipped') return '按需跳过'
  return '待命'
}

function stepStatusText(step: TimelineStep) {
  if (step.status) return step.status
  if (props.live) return '思考中'
  return '已记录'
}

function iconForAgent(agentId: string) {
  if (agentId === 'orchestrator') return Sparkles
  if (agentId === 'code-researcher') return FileSearch
  if (agentId === 'impact-analyzer') return GitBranch
  if (agentId === 'product-manager') return MessageSquareText
  if (agentId === 'test-manager') return ClipboardCheck
  return Wrench
}

function stepLabel(index: number) {
  return String(index + 1).padStart(2, '0')
}

function rendered(content: string) {
  return renderMarkdown(content)
}
</script>

<template>
  <details v-if="steps.length" ref="rootRef" class="analysis-timeline">
    <summary class="timeline-summary-head">
      <div>
        <p class="timeline-kicker">Multi-Agent Collaboration</p>
        <h4>{{ live ? currentActivity : '协作回放' }}</h4>
      </div>
      <span class="timeline-meta">
        {{ steps.length }} 个步骤 / {{ eventCount }} 个事件
      </span>
      <ChevronDown class="timeline-chevron" :size="16" :stroke-width="2" aria-hidden="true" />
    </summary>

    <div class="timeline-body-wrap">
      <div class="timeline-header">
        <div>
          <p class="timeline-kicker">Agent 活动</p>
          <h4>{{ live ? '实时协作详情' : '协作回放详情' }}</h4>
        </div>
        <span class="timeline-meta">
          {{ steps.length }} 个活动 Agent
        </span>
      </div>

      <div class="collaboration-map">
        <div class="agent-node-grid">
        <article
          v-for="node in collaborationNodes"
          :key="node.id"
          class="agent-node"
          :class="`is-${node.status}`"
        >
          <div class="agent-node-icon" aria-hidden="true">
            <component :is="iconForAgent(node.id)" :size="16" :stroke-width="2" />
          </div>
          <div class="agent-node-main">
            <span class="agent-node-role">{{ node.role }}</span>
            <strong>{{ node.name }}</strong>
            <p>{{ node.description }}</p>
            <span class="agent-node-metrics">{{ node.metrics }}</span>
          </div>
          <span class="agent-node-status">{{ statusText(node.status) }}</span>
        </article>
        </div>

        <div v-if="collaborationHandoffs.length" class="handoff-lane" aria-label="Agent 交接链路">
        <div
          v-for="handoff in collaborationHandoffs"
          :key="handoff.id"
          class="handoff-card"
          :class="`is-${handoff.status}`"
        >
          <div class="handoff-route">
            <span>{{ handoff.from }}</span>
            <ArrowRight :size="14" :stroke-width="2" aria-hidden="true" />
            <span>{{ handoff.to }}</span>
          </div>
          <div class="handoff-copy">
            <strong>{{ handoff.title }}</strong>
            <p>{{ handoff.description }}</p>
          </div>
          <span class="handoff-status">{{ statusText(handoff.status) }}</span>
        </div>
        </div>
      </div>

      <div class="timeline-list">
      <details
        v-for="(step, index) in steps"
        :key="step.id"
        class="timeline-step"
        :class="`is-${stepState(index)}`"
      >
        <summary class="timeline-summary">
          <span class="timeline-rail" aria-hidden="true">
            <span class="timeline-node">
              <CheckCircle2 v-if="stepState(index) === 'done'" :size="16" :stroke-width="2" />
              <CircleDashed v-else :size="16" :stroke-width="2" />
            </span>
          </span>
          <span class="timeline-card-head">
            <span class="timeline-step-title">
              <span class="timeline-step-index">{{ stepLabel(index) }}</span>
              {{ step.title }}
            </span>
            <span class="timeline-step-subtitle">{{ step.subtitle }}</span>
          </span>
          <span class="timeline-step-badges">
            <span v-if="step.toolEvents.length" class="timeline-badge">
              <Wrench :size="13" :stroke-width="2" />
              {{ step.toolEvents.length }}
            </span>
            <span class="timeline-status">{{ stepStatusText(step) }}</span>
          </span>
        </summary>

        <div class="timeline-body">
          <div v-if="step.toolEvents.length" class="timeline-tool-strip">
            <span
              v-for="(toolEvent, toolIndex) in step.toolEvents"
              :key="`${step.id}-${toolEvent.type}-${toolEvent.name}-${toolIndex}`"
              class="timeline-tool-chip"
              :class="`is-${toolEvent.type}`"
            >
              <Wrench v-if="toolEvent.type === 'call'" :size="13" :stroke-width="2" />
              <FileSearch v-else :size="13" :stroke-width="2" />
              {{ toolEvent.type === 'call' ? '调用' : '结果' }} {{ toolEvent.name }}
            </span>
          </div>

          <div
            v-if="step.reasoning"
            class="timeline-markdown markdown-body"
            v-html="rendered(step.reasoning)"
          />

          <div
            v-if="step.result"
            class="timeline-result markdown-body"
            v-html="rendered(step.result)"
          />

          <div v-if="step.toolEvents.some((item) => item.content)" class="timeline-tool-details">
            <details
              v-for="(toolEvent, toolIndex) in step.toolEvents.filter((item) => item.content)"
              :key="`${step.id}-detail-${toolIndex}`"
              class="timeline-tool-detail"
            >
              <summary>{{ toolEvent.type === 'call' ? '工具输入' : '工具输出' }}：{{ toolEvent.name }}</summary>
              <pre>{{ toolEvent.content }}</pre>
            </details>
          </div>
        </div>
      </details>
      </div>
    </div>
  </details>
</template>

<style scoped>
.analysis-timeline {
  display: grid;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background:
    linear-gradient(180deg, color-mix(in srgb, var(--surface) 92%, var(--chat-accent) 8%), var(--surface));
}

.timeline-summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  min-width: 0;
  cursor: pointer;
  list-style: none;
}

.timeline-summary-head::-webkit-details-marker {
  display: none;
}

.timeline-summary-head h4 {
  margin: var(--spacing-1) 0 0;
  color: var(--text);
  font-size: var(--font-size-md);
}

.timeline-chevron {
  flex: 0 0 auto;
  color: var(--muted);
  transition: transform var(--transition-fast);
}

.analysis-timeline[open] .timeline-chevron {
  transform: rotate(180deg);
}

.timeline-body-wrap {
  display: grid;
  gap: var(--spacing-3);
  padding-top: var(--spacing-3);
}

.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
}

.timeline-kicker {
  margin: 0;
  color: var(--chat-timestamp);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.timeline-header h4 {
  margin: var(--spacing-1) 0 0;
  color: var(--text);
  font-size: var(--font-size-md);
}

.timeline-meta {
  flex: 0 0 auto;
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.collaboration-map {
  display: grid;
  gap: var(--spacing-3);
}

.agent-node-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(210px, 1fr));
  gap: var(--spacing-2);
}

.agent-node {
  position: relative;
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: var(--spacing-2);
  min-width: 0;
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background:
    radial-gradient(circle at 16% 0%, rgba(79, 110, 247, 0.1), transparent 40%),
    var(--surface);
  overflow: hidden;
}

.agent-node::after {
  content: '';
  position: absolute;
  inset: auto var(--spacing-3) 0;
  height: 2px;
  border-radius: var(--radius-full);
  background: var(--stroke);
}

.agent-node.is-active {
  border-color: color-mix(in srgb, var(--chat-accent) 34%, var(--stroke));
  box-shadow: 0 10px 28px rgba(79, 110, 247, 0.12);
}

.agent-node.is-active::after {
  background: linear-gradient(90deg, transparent, var(--chat-accent), transparent);
  animation: collaborationFlow 1.6s ease-in-out infinite;
}

.agent-node.is-done::after {
  background: var(--success);
}

.agent-node.is-skipped {
  opacity: 0.68;
}

.agent-node-icon {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border-radius: var(--radius-md);
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
}

.agent-node-main {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.agent-node-role {
  color: var(--chat-timestamp);
  font-size: 10px;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.agent-node-main strong {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-size-sm);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-node-main p {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  min-height: 34px;
  overflow: hidden;
  margin: 0;
  color: var(--muted);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-normal);
}

.agent-node-metrics {
  min-width: 0;
  overflow: hidden;
  color: var(--subtle);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.agent-node-status,
.handoff-status {
  justify-self: start;
  display: inline-flex;
  align-items: center;
  border-radius: var(--radius-full);
  background: var(--surface-soft);
  padding: 2px var(--spacing-2);
  color: var(--muted);
  font-size: 10px;
  font-weight: var(--font-weight-medium);
  white-space: nowrap;
}

.agent-node.is-active .agent-node-status,
.handoff-card.is-active .handoff-status {
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
}

.agent-node.is-done .agent-node-status,
.handoff-card.is-done .handoff-status {
  background: rgba(5, 150, 105, 0.08);
  color: var(--success);
}

.handoff-lane {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-2);
}

.handoff-card {
  position: relative;
  display: grid;
  gap: var(--spacing-2);
  min-width: 0;
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--surface) 90%, var(--surface-soft) 10%);
}

.handoff-card.is-active {
  border-color: color-mix(in srgb, var(--chat-accent) 28%, var(--stroke));
}

.handoff-card.is-skipped {
  opacity: 0.62;
}

.handoff-route {
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  min-width: 0;
  color: var(--chat-accent);
  font-size: 10px;
  font-weight: var(--font-weight-semibold);
}

.handoff-route span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.handoff-copy {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.handoff-copy strong {
  color: var(--text);
  font-size: var(--font-size-xs);
}

.handoff-copy p {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  min-height: 32px;
  overflow: hidden;
  margin: 0;
  color: var(--muted);
  font-size: 10px;
  line-height: var(--line-height-normal);
}

.timeline-list {
  display: grid;
  gap: var(--spacing-2);
}

.timeline-step {
  position: relative;
  overflow: hidden;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--surface) 92%, var(--surface-soft) 8%);
}

.timeline-step::before {
  content: '';
  position: absolute;
  top: 40px;
  bottom: -12px;
  left: 25px;
  width: 1px;
  background: var(--stroke);
}

.timeline-step:last-child::before {
  display: none;
}

.timeline-step[open] {
  border-color: color-mix(in srgb, var(--chat-accent) 24%, var(--stroke));
  box-shadow: var(--shadow-soft);
}

.timeline-summary {
  display: grid;
  grid-template-columns: 34px minmax(0, 1fr) auto;
  align-items: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3);
  cursor: pointer;
  list-style: none;
}

.timeline-summary::-webkit-details-marker {
  display: none;
}

.timeline-rail {
  display: grid;
  place-items: center;
  align-self: stretch;
}

.timeline-node {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--stroke);
  border-radius: 50%;
  background: var(--surface);
  color: var(--success);
  z-index: 1;
}

.timeline-step.is-active .timeline-node {
  color: var(--chat-accent);
  animation: timelinePulse 1.5s ease-in-out infinite;
}

.timeline-card-head {
  min-width: 0;
  display: grid;
  gap: 2px;
}

.timeline-step-title {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-step-index {
  margin-right: var(--spacing-2);
  color: var(--chat-timestamp);
  font-size: var(--font-size-xs);
  font-family: "SF Mono", "JetBrains Mono", Menlo, Consolas, monospace;
}

.timeline-step-subtitle {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-step-badges {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
}

.timeline-badge,
.timeline-status,
.timeline-tool-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  white-space: nowrap;
}

.timeline-badge {
  padding: 2px var(--spacing-2);
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
}

.timeline-status {
  padding: 2px var(--spacing-2);
  background: var(--surface-soft);
  color: var(--muted);
}

.timeline-body {
  display: grid;
  gap: var(--spacing-3);
  margin-left: 47px;
  padding: 0 var(--spacing-3) var(--spacing-3);
}

.timeline-tool-strip {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.timeline-tool-chip {
  padding: var(--spacing-1) var(--spacing-2);
  border: 1px solid rgba(79, 110, 247, 0.14);
  background: rgba(79, 110, 247, 0.05);
  color: var(--chat-accent);
}

.timeline-tool-chip.is-result {
  border-color: rgba(5, 150, 105, 0.16);
  background: rgba(5, 150, 105, 0.06);
  color: var(--success);
}

.timeline-markdown,
.timeline-result {
  max-height: 260px;
  overflow: auto;
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.timeline-result {
  border-color: rgba(5, 150, 105, 0.16);
  background: rgba(5, 150, 105, 0.04);
}

.timeline-tool-details {
  display: grid;
  gap: var(--spacing-2);
}

.timeline-tool-detail {
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.timeline-tool-detail summary {
  padding: var(--spacing-2) var(--spacing-3);
  cursor: pointer;
  color: var(--subtle);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.timeline-tool-detail pre {
  max-height: 180px;
  overflow: auto;
  margin: 0;
  padding: 0 var(--spacing-3) var(--spacing-3);
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--muted);
  font-size: var(--font-size-xs);
  font-family: "SF Mono", "JetBrains Mono", Menlo, Consolas, monospace;
}

@keyframes timelinePulse {
  0%, 100% {
    box-shadow: 0 0 0 0 rgba(79, 110, 247, 0.22);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(79, 110, 247, 0.08);
  }
}

@keyframes collaborationFlow {
  0%, 100% {
    opacity: 0.35;
    transform: translateX(-16%);
  }
  50% {
    opacity: 1;
    transform: translateX(16%);
  }
}

@media (max-width: 720px) {
  .analysis-timeline {
    padding: var(--spacing-3);
  }

  .agent-node-grid,
  .handoff-lane {
    grid-template-columns: 1fr;
  }

  .timeline-header,
  .timeline-summary {
    align-items: start;
  }

  .timeline-summary {
    grid-template-columns: 30px minmax(0, 1fr);
  }

  .timeline-step-badges {
    grid-column: 2;
    justify-self: start;
  }

  .timeline-body {
    margin-left: 43px;
  }
}
</style>
