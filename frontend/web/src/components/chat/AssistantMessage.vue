<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import type { QuestionRecord } from '../../types/question'
import { renderMarkdown, renderMermaidBlocks } from '../../utils/markdown'
import QuestionStatusTag from './StatusTag.vue'
import AnswerEvidence from './AnswerEvidence.vue'
import QueryPlanCard from './QueryPlanCard.vue'
import AnalysisTimeline from './AnalysisTimeline.vue'
import { useQuestionStore } from '../../stores/question'

const props = defineProps<{
  question: QuestionRecord
}>()

const questionStore = useQuestionStore()
const answerRef = ref<HTMLElement | null>(null)
const renderedAnswer = computed(() => renderMarkdown(props.question.answer))

const liveState = computed(() => questionStore.liveStateFor(props.question.id))
const replayAgents = computed(() => liveState.value
  ? Object.values(liveState.value.streamingAgents).filter(a => a.status || a.reasoning || a.result || a.toolEvents.length)
  : [])
const replayHandoffs = computed(() => liveState.value?.streamingHandoffs ?? [])

function resumeTask() {
  questionStore.retryQuestion(props.question.id)
}

watch(
  [renderedAnswer, () => props.question.status],
  () => {
    nextTick(() => {
      if (answerRef.value) renderMermaidBlocks(answerRef.value)
    })
  },
  { immediate: true }
)
</script>

<template>
  <article class="message-bubble message-assistant">
    <div class="message-head">
      <div>
        <p class="bubble-label">Ascoder 回答</p>
        <h3 class="assistant-title">执行结果</h3>
      </div>
      <QuestionStatusTag :status="question.status" />
    </div>

    <QueryPlanCard v-if="question.queryPlan" :query-plan="question.queryPlan" />

    <div v-if="question.status === 'PENDING' || question.status === 'RUNNING'" class="answer-loading">
      <div class="loading-spark" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <div>
        <strong>{{ question.status === 'PENDING' ? '等待处理中' : '分析中' }}</strong>
        <span>{{ question.status === 'PENDING' ? '问题已进入队列，等待 Agent 接手。' : '正在根据 QueryPlan 调用工具并组织回答。' }}</span>
      </div>
    </div>

    <template v-else>
      <AnalysisTimeline
        v-if="question.analysisProcess || replayAgents.length || replayHandoffs.length"
        :agents="replayAgents"
        :handoffs="replayHandoffs"
        :markdown="question.analysisProcess"
      />

      <section v-if="question.answer" class="answer-panel">
        <div class="answer-panel-head">答案输出</div>
        <div ref="answerRef" class="answer-content markdown-body" v-html="renderedAnswer" />
      </section>
      <p v-else-if="question.status === 'SUCCEEDED'" class="empty-copy">当前问题还没有返回可展示的回答内容。</p>
    </template>

    <AnswerEvidence
      v-if="question.answerSummary || question.answerEvidence.length || question.uncertainty || question.nextStep"
      :summary="question.answerSummary"
      :evidence="question.answerEvidence"
      :uncertainty="question.uncertainty"
      :next-step="question.nextStep"
    />

    <div v-if="question.errorMessage" class="error-panel">
      <el-alert
        type="error"
        :title="question.errorMessage"
        show-icon
        :closable="false"
      />
      <button
        v-if="question.status === 'FAILED'"
        class="resume-button"
        type="button"
        :disabled="Boolean(questionStore.liveStateFor(question.id)?.streaming)"
        @click="resumeTask"
      >
        重试
      </button>
    </div>
  </article>
</template>

<script lang="ts">
export default {}
</script>

<style scoped>
.assistant-title {
  margin: var(--spacing-1) 0 0;
  color: var(--text);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.answer-panel {
  display: grid;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.answer-panel-head {
  color: var(--chat-timestamp);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.analysis-process {
  overflow: hidden;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: color-mix(in srgb, var(--surface) 82%, var(--muted) 18%);
  color: var(--muted);
}

.analysis-process summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  cursor: pointer;
  user-select: none;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.analysis-process summary::-webkit-details-marker {
  display: none;
}

.analysis-process summary::before {
  content: '▸';
  color: var(--chat-timestamp);
  transition: transform var(--transition-fast);
}

.analysis-process[open] summary::before {
  transform: rotate(90deg);
}

.analysis-process summary span {
  flex: 1;
}

.analysis-process summary small {
  color: var(--chat-timestamp);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-regular);
}

.analysis-process-content {
  max-height: 420px;
  overflow: auto;
  padding: 0 var(--spacing-4) var(--spacing-4);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.analysis-process-content :deep(*) {
  color: inherit;
}

.error-panel {
  display: grid;
  gap: var(--spacing-3);
}

.resume-button {
  justify-self: start;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-full);
  padding: var(--spacing-2) var(--spacing-4);
  background: var(--surface);
  color: var(--primary);
  cursor: pointer;
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.resume-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.loading-spark {
  position: relative;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--chat-accent-soft);
}

.loading-spark span {
  position: absolute;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--chat-accent);
  animation: loadingSpark 1.35s ease-in-out infinite;
}

.loading-spark span:nth-child(1) {
  top: 9px;
  left: 15px;
}

.loading-spark span:nth-child(2) {
  top: 20px;
  left: 9px;
  animation-delay: 0.15s;
}

.loading-spark span:nth-child(3) {
  top: 20px;
  right: 9px;
  animation-delay: 0.3s;
}

.answer-loading {
  align-items: center;
  padding: var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.14);
  border-radius: var(--radius-lg);
  background: var(--chat-accent-soft);
}

.answer-loading strong,
.answer-loading span {
  display: block;
}

.answer-loading strong {
  color: var(--text);
  font-size: var(--font-size-md);
}

.answer-loading span {
  margin-top: 2px;
  color: var(--muted);
  font-size: var(--font-size-sm);
}

@keyframes loadingSpark {
  0%, 100% {
    opacity: 0.45;
    transform: translateY(0) scale(0.82);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px) scale(1);
  }
}
</style>
