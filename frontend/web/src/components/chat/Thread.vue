<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { animate } from 'motion-v'
import { usePreferredReducedMotion } from '@vueuse/core'
import { ArrowDown } from 'lucide-vue-next'
import { useQuestionStore } from '../../stores/question'
import type { QuestionRecord } from '../../types/question'
import type { QuestionStreamState } from '../../stores/questionStreamState'
import { renderMarkdown, renderMermaidBlocks } from '../../utils/markdown'
import UserMessage from './UserMessage.vue'
import AssistantMessage from './AssistantMessage.vue'
import QueryPlanCard from './QueryPlanCard.vue'
import AnalysisTimeline from './AnalysisTimeline.vue'

const questionStore = useQuestionStore()
const prefersReducedMotion = usePreferredReducedMotion()
const scrollContainer = ref<HTMLElement | null>(null)
const streamingTextRef = ref<HTMLElement | null>(null)
const isUserScrolling = ref(false)
let scrollTimeout: ReturnType<typeof setTimeout> | null = null

const threadQuestions = computed(() => {
  if (questionStore.viewingQuestionId && questionStore.activeQuestion) {
    return [questionStore.activeQuestion]
  }
  if (questionStore.activeConversationQuestions.length > 0) {
    return questionStore.activeConversationQuestions
  }
  const active = questionStore.activeQuestion
  return active ? [active] : []
})

function renderedStreamingContent(content: string) {
  return renderMarkdown(content)
}

/** 聊天项目扁平列表，用于虚拟滚动 */
const virtualItems = computed(() => {
  const items: Array<
    | { type: 'divider'; index: number }
    | { type: 'user'; question: QuestionRecord }
    | { type: 'assistant'; question: QuestionRecord }
    | { type: 'streaming'; question: QuestionRecord | null; liveState: QuestionStreamState }
  > = []

  threadQuestions.value.forEach((question, index) => {
    if (index > 0) {
      items.push({ type: 'divider', index })
    }
    items.push({ type: 'user', question })
    const liveState = questionStore.liveStateFor(question.id)
    if (liveState?.streaming) {
      items.push({ type: 'streaming', question, liveState })
    } else {
      items.push({ type: 'assistant', question })
    }
  })

  if (questionStore.activeStreamState?.streaming && questionStore.streamingQuestionId == null) {
    items.push({ type: 'streaming', question: null, liveState: questionStore.activeStreamState })
  }

  return items
})

/** 自动滚动到底部 */
function scrollToBottom(smooth = true) {
  if (!scrollContainer.value || isUserScrolling.value) return
  scrollContainer.value.scrollTo({
    top: scrollContainer.value.scrollHeight,
    behavior: smooth ? 'smooth' : 'instant',
  })
}

function onScroll() {
  if (!scrollContainer.value) return
  const el = scrollContainer.value
  const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight
  isUserScrolling.value = distanceFromBottom > 80

  if (!isUserScrolling.value) {
    if (scrollTimeout) clearTimeout(scrollTimeout)
    scrollTimeout = null
  }
}

/** 监听流式内容变化自动滚动 */
watch(
  () => questionStore.streamingContent + questionStore.streamingStatus,
  () => nextTick(() => {
    if (streamingTextRef.value) renderMermaidBlocks(streamingTextRef.value)
    scrollToBottom(true)
  })
)

/** 监听消息列表变化滚动到底部（新消息） */
watch(
  () => threadQuestions.value.length,
  () => nextTick(() => scrollToBottom(true))
)

/** 新消息未读提示点击 */
function onNewMessageClick() {
  isUserScrolling.value = false
  scrollToBottom(true)
}

/** Apple 风格弹簧入场：对新消息气泡应用可中断弹簧动画 */
const animatedCount = ref(0)
watch(
  () => virtualItems.value.length,
  (newLen) => {
    if (newLen <= animatedCount.value) {
      animatedCount.value = newLen
      return
    }
    const prevLen = animatedCount.value
    animatedCount.value = newLen
    nextTick(() => {
      const bubbles = scrollContainer.value?.querySelectorAll('.message-bubble')
      if (!bubbles) return
      // 仅对新增的气泡做入场动画
      for (let i = prevLen; i < bubbles.length; i++) {
        const el = bubbles[i] as HTMLElement
        if (prefersReducedMotion.value === 'reduce') {
          animate(el, { opacity: [0, 1] }, { duration: 0.2 })
        } else {
          animate(el, { opacity: [0, 1], y: [10, 0] }, {
            type: 'spring',
            bounce: 0,
            duration: 0.3,
          })
        }
      }
    })
  },
)

defineExpose({ scrollToBottom })
</script>

<template>
  <div class="thread-wrapper">
    <div
      ref="scrollContainer"
      class="chat-scroll-area"
      @scroll="onScroll"
    >
      <div class="thread-stack">
        <template v-for="(item, i) in virtualItems" :key="i">
          <div v-if="item.type === 'divider'" class="turn-divider">
            <span class="turn-label">第 {{ item.index + 1 }} 轮</span>
          </div>
          <UserMessage
            v-else-if="item.type === 'user'"
            :question="item.question"
          />
          <AssistantMessage
            v-else-if="item.type === 'assistant'"
            :question="item.question"
          />
          <!-- 流式回答显示 -->
          <article v-else-if="item.type === 'streaming'" class="streaming-message message-bubble message-assistant">
            <div class="message-head">
              <div>
                <p class="bubble-label">Ascoder 回答</p>
                <h3 class="streaming-title">正在形成答案</h3>
              </div>
              <span class="live-pill">
                <span class="live-dot" />
                LIVE
              </span>
            </div>

            <QueryPlanCard v-if="item.question?.queryPlan" :query-plan="item.question.queryPlan" />

            <section class="thinking-panel">
              <div class="thinking-orb" aria-hidden="true">
                <span />
                <span />
                <span />
              </div>
              <div class="thinking-copy">
                <p class="thinking-title">分析中</p>
                <p class="thinking-status">
                  {{ item.liveState.streamingStatus || '正在拆解问题、选择工具并收集证据…' }}
                </p>
              </div>
            </section>

            <AnalysisTimeline
              v-if="(item.question && questionStore.agentListFor(item.question.id).length) || item.question?.analysisProcess"
              :agents="item.question ? questionStore.agentListFor(item.question.id) : []"
              :handoffs="item.liveState.streamingHandoffs"
              :markdown="item.question?.analysisProcess"
              live
            />

            <section class="streaming-answer">
              <div class="streaming-answer-head">
                <span>答案输出</span>
                <span v-if="item.liveState.streamingContent" class="answer-cursor">正在生成</span>
              </div>
              <div
                v-if="item.liveState.streamingContent"
                ref="streamingTextRef"
                class="streaming-text markdown-body"
                v-html="renderedStreamingContent(item.liveState.streamingContent)"
              />
              <div v-else class="answer-placeholder">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>等待模型返回第一段内容…</span>
              </div>
            </section>
          </article>
        </template>
      </div>
    </div>

    <!-- 新消息未读提示 -->
    <Transition name="fade">
      <button
        v-if="isUserScrolling && questionStore.activeStreaming"
        class="new-message-hint"
        type="button"
        title="查看新消息"
        aria-label="查看新消息"
        @click="onNewMessageClick"
      >
        <span>新消息</span>
        <ArrowDown class="hint-arrow" aria-hidden="true" :size="14" :stroke-width="1.8" />
      </button>
    </Transition>
  </div>
</template>

<script lang="ts">
import { Loading } from '@element-plus/icons-vue'
export default { components: { Loading } }
</script>

<style scoped>
.thread-wrapper {
  position: relative;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.chat-scroll-area {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  padding: var(--spacing-8) 0;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: var(--scrollbar-thumb) var(--scrollbar-track);
}

.chat-scroll-area::-webkit-scrollbar {
  width: var(--scrollbar-size);
}

.chat-scroll-area::-webkit-scrollbar-track {
  background: transparent;
}

.chat-scroll-area::-webkit-scrollbar-thumb {
  background: var(--scrollbar-thumb);
  border-radius: var(--radius-full);
}

.chat-scroll-area::-webkit-scrollbar-thumb:hover {
  background: var(--scrollbar-thumb-hover);
}

/* 虚拟滚动：仅渲染可见区域 */
.chat-scroll-area .thread-stack > * {
  content-visibility: auto;
  contain-intrinsic-size: 0 120px;
}

/* ── 流式消息 ── */

.streaming-message {
  position: relative;
  min-width: 0;
  padding: var(--spacing-5);
  overflow: hidden;
  background:
    radial-gradient(circle at 12% 0%, rgba(79, 110, 247, 0.1), transparent 34%),
    linear-gradient(180deg, var(--surface), rgba(255, 255, 255, 0.82));
  border: 1px solid rgba(79, 110, 247, 0.14);
  box-shadow: var(--shadow-panel);
  /* 入场由 motion-v 弹簧驱动，此处不再设置 animation */
}

.streaming-message::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(79, 110, 247, 0.18), transparent 28%, transparent 72%, rgba(79, 110, 247, 0.08));
  opacity: 0.38;
}

.streaming-message > * {
  position: relative;
}

.streaming-title {
  margin: var(--spacing-1) 0 0;
  color: var(--text);
  font-size: var(--font-size-xl);
  font-weight: var(--font-weight-semibold);
}

.live-pill {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-1) var(--spacing-3);
  border: 1px solid rgba(79, 110, 247, 0.24);
  border-radius: var(--radius-full);
  background: rgba(79, 110, 247, 0.08);
  color: var(--chat-accent);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.04em;
}

.live-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--chat-accent);
  box-shadow: 0 0 0 0 rgba(79, 110, 247, 0.35);
  animation: livePulse 1.6s ease-out infinite;
}

.thinking-panel {
  display: flex;
  align-items: center;
  gap: var(--spacing-4);
  padding: var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.14);
  border-radius: var(--radius-lg);
  background: rgba(79, 110, 247, 0.06);
}

.thinking-orb {
  position: relative;
  width: 40px;
  height: 40px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: linear-gradient(135deg, rgba(79, 110, 247, 0.16), rgba(79, 110, 247, 0.04));
}

.thinking-orb span {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--chat-accent);
  animation: orbitDot 1.4s ease-in-out infinite;
}

.thinking-orb span:nth-child(1) {
  top: 9px;
  left: 16px;
}

.thinking-orb span:nth-child(2) {
  top: 21px;
  left: 9px;
  animation-delay: 0.15s;
}

.thinking-orb span:nth-child(3) {
  top: 21px;
  right: 9px;
  animation-delay: 0.3s;
}

.thinking-copy {
  min-width: 0;
}

.thinking-title {
  margin: 0;
  color: var(--text);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.thinking-status {
  margin: var(--spacing-1) 0 0;
  color: var(--muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.process-panel {
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.66);
  overflow: hidden;
}

.process-panel summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  cursor: pointer;
  user-select: none;
  color: var(--subtle);
  list-style: none;
  transition:
    background var(--transition-fast),
    color var(--transition-fast);
}

.process-panel summary::-webkit-details-marker {
  display: none;
}

.process-panel summary::before {
  content: '+';
  display: inline-grid;
  place-items: center;
  width: 20px;
  height: 20px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
  font-weight: var(--font-weight-semibold);
}

.process-panel[open] summary::before {
  content: '-';
}

.process-panel summary:hover {
  background: var(--chat-accent-soft);
  color: var(--text);
}

.process-summary-main {
  margin-right: auto;
  font-weight: var(--font-weight-semibold);
}

.process-summary-meta {
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.agent-event-list,
.tool-event-list {
  display: grid;
  gap: var(--spacing-2);
}

.agent-event-list {
  padding: 0 var(--spacing-4) var(--spacing-4);
}

.agent-event {
  padding: var(--spacing-3);
  background: var(--surface);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  transition: border-color var(--transition-fast);
}

.agent-event:hover {
  border-color: var(--stroke-strong);
}

.agent-event-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-2);
}

.agent-event-name {
  display: block;
  color: var(--text);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-sm);
}

.agent-event-status {
  display: block;
  margin-top: 2px;
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.agent-event-path {
  overflow: hidden;
  color: var(--muted);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "SF Mono", "JetBrains Mono", Menlo, Consolas, monospace;
}

.agent-reasoning,
.agent-result {
  max-height: 220px;
  overflow: auto;
  margin-bottom: var(--spacing-2);
  padding: var(--spacing-2);
  background: var(--surface);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-sm);
}

.tool-event {
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid rgba(79, 110, 247, 0.14);
  border-left: 3px solid var(--chat-accent);
  border-radius: var(--radius-md);
  background: rgba(79, 110, 247, 0.04);
}

.tool-event-result {
  border-color: rgba(5, 150, 105, 0.16);
  border-left-color: var(--success);
  background: rgba(5, 150, 105, 0.05);
}

.tool-event-type {
  color: var(--chat-accent);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-xs);
}

.tool-event-name {
  margin-left: var(--spacing-2);
  color: var(--text);
  font-size: var(--font-size-sm);
}

.tool-event pre {
  max-height: 160px;
  overflow: auto;
  margin: var(--spacing-1) 0 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: var(--font-size-xs);
  font-family: "SF Mono", "JetBrains Mono", Menlo, Consolas, monospace;
  color: var(--muted);
}

.streaming-text {
  min-width: 0;
  max-width: 100%;
  line-height: var(--line-height-relaxed);
}

.streaming-answer {
  display: grid;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.streaming-answer-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  color: var(--chat-timestamp);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.answer-cursor {
  color: var(--chat-accent);
  letter-spacing: 0.02em;
}

.answer-placeholder {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.answer-placeholder .el-icon {
  color: var(--chat-accent);
}

@keyframes livePulse {
  0% {
    box-shadow: 0 0 0 0 rgba(79, 110, 247, 0.35);
  }
  100% {
    box-shadow: 0 0 0 9px rgba(79, 110, 247, 0);
  }
}

@keyframes orbitDot {
  0%, 100% {
    opacity: 0.45;
    transform: translateY(0) scale(0.82);
  }
  50% {
    opacity: 1;
    transform: translateY(-3px) scale(1);
  }
}

:global([data-theme="dark"]) .streaming-message {
  background:
    radial-gradient(circle at 12% 0%, rgba(107, 138, 247, 0.14), transparent 34%),
    linear-gradient(180deg, var(--surface), rgba(24, 24, 29, 0.82));
}

:global([data-theme="dark"]) .process-panel {
  background: rgba(30, 30, 36, 0.68);
}

@media (prefers-color-scheme: dark) {
  :global(:root:not([data-theme="light"])) .streaming-message {
    background:
      radial-gradient(circle at 12% 0%, rgba(107, 138, 247, 0.14), transparent 34%),
      linear-gradient(180deg, var(--surface), rgba(24, 24, 29, 0.82));
  }

  :global(:root:not([data-theme="light"])) .process-panel {
    background: rgba(30, 30, 36, 0.68);
  }
}

/* ── 轮次分割线 ── */

.turn-divider {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  margin: var(--spacing-5) 0 var(--spacing-3);
}

.turn-divider::before,
.turn-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--chat-divider);
}

.turn-label {
  font-size: var(--font-size-xs);
  color: var(--chat-timestamp);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0.02em;
  flex-shrink: 0;
}

/* ── 新消息提示 ── */

.new-message-hint {
  position: absolute;
  bottom: var(--spacing-4);
  left: 50%;
  transform: translateX(-50%);
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  padding: var(--spacing-2) var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-full);
  background: var(--surface);
  color: var(--chat-accent);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  cursor: pointer;
  box-shadow: var(--shadow-elevated);
  z-index: 5;
  transition:
    background var(--transition-fast),
    transform var(--press-duration) var(--ease-snappy),
    box-shadow var(--transition-fast);
}

.new-message-hint:hover {
  background: var(--chat-accent-soft);
  transform: translateX(-50%) translateY(-1px);
  box-shadow: var(--shadow-panel);
}

.new-message-hint:active {
  transform: translateX(-50%) scale(var(--press-scale));
}

.hint-arrow {
  font-size: var(--font-size-xs);
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

</style>
