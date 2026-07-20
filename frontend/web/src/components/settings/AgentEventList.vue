<template>
  <div class="agent-event-list">
    <div v-if="loading && events.length === 0" class="event-empty">加载中…</div>
    <div v-else-if="events.length === 0" class="event-empty">暂无事件</div>
    <div v-for="event in events" :key="event.id" class="event-item" :class="eventClass(event.eventType)">
      <span class="event-seq">#{{ event.sequenceNo }}</span>
      <span class="event-type-badge" :class="eventClass(event.eventType)">{{ event.eventType }}</span>
      <span class="event-time">{{ formatTime(event.createdAt) }}</span>
      <pre v-if="eventPayloadText(event)" class="event-payload">{{ eventPayloadText(event) }}</pre>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount, watch } from 'vue'
import { getAgentEvents, type AgentEventRecord } from '../../services/questionApi'

const props = defineProps<{
  questionId: number
  autoPoll?: boolean
}>()

const events = ref<AgentEventRecord[]>([])
const loading = ref(false)
let pollTimer: ReturnType<typeof setInterval> | null = null
let lastMaxSeq = 0

const POLL_INTERVAL = 1500

async function loadEvents() {
  if (!props.questionId) return
  loading.value = true
  try {
    const all = await getAgentEvents(props.questionId)
    const sorted = all.sort((a, b) => a.sequenceNo - b.sequenceNo)
    events.value = sorted
    lastMaxSeq = sorted.length > 0 ? sorted[sorted.length - 1].sequenceNo : 0
  } catch {
    // ignore
  } finally {
    loading.value = false
  }
}

async function pollEvents() {
  if (!props.questionId) return
  try {
    const all = await getAgentEvents(props.questionId)
    const sorted = all.sort((a, b) => a.sequenceNo - b.sequenceNo)
    const newEvents = sorted.filter(e => e.sequenceNo > lastMaxSeq)
    if (newEvents.length > 0) {
      events.value = [...events.value, ...newEvents]
      lastMaxSeq = newEvents[newEvents.length - 1].sequenceNo
    }
  } catch {
    // ignore
  }
}

function startPolling() {
  stopPolling()
  pollTimer = setInterval(pollEvents, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer !== null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function eventClass(type: string): string {
  if (type === 'reasoning') return 'event-reasoning'
  if (type === 'tool_call') return 'event-tool-call'
  if (type === 'tool_result') return 'event-tool-result'
  if (type === 'summary') return 'event-summary'
  if (type === 'result') return 'event-result'
  if (type === 'handoff') return 'event-handoff'
  return 'event-other'
}

function eventPayloadText(event: AgentEventRecord): string {
  if (!event.payload) return ''
  if (typeof event.payload === 'string') return event.payload
  try {
    const obj = event.payload as Record<string, unknown>
    // 提取可读字段
    if (obj.content) return String(obj.content)
    if (obj.text) return String(obj.text)
    if (obj.message) return String(obj.message)
    return JSON.stringify(event.payload, null, 2)
  } catch {
    return ''
  }
}

function formatTime(iso: string): string {
  try {
    return new Date(iso).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  } catch {
    return ''
  }
}

// 初始加载
loadEvents()

// autoPoll 控制
watch(() => props.autoPoll, (val) => {
  if (val) {
    startPolling()
  } else {
    stopPolling()
  }
}, { immediate: true })

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.agent-event-list {
  max-height: 400px;
  overflow-y: auto;
  font-size: var(--font-size-sm, 13px);
}
.event-empty {
  color: var(--muted, #999);
  text-align: center;
  padding: 12px;
}
.event-item {
  padding: 4px 8px;
  border-left: 3px solid var(--muted, #ccc);
  margin-bottom: 4px;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 6px;
}
.event-seq {
  color: var(--muted, #999);
  font-size: 11px;
}
.event-type-badge {
  display: inline-block;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  background: var(--muted, #999);
}
.event-reasoning .event-type-badge { background: #3498db; }
.event-tool-call .event-type-badge { background: #e67e22; }
.event-tool-result .event-type-badge { background: #f39c12; }
.event-summary .event-type-badge { background: #27ae60; }
.event-result .event-type-badge { background: #2ecc71; }
.event-handoff .event-type-badge { background: #9b59b6; }
.event-other .event-type-badge { background: #95a5a6; }
.event-item.event-reasoning { border-left-color: #3498db; }
.event-item.event-tool-call { border-left-color: #e67e22; }
.event-item.event-tool-result { border-left-color: #f39c12; }
.event-item.event-summary { border-left-color: #27ae60; }
.event-item.event-result { border-left-color: #2ecc71; }
.event-item.event-handoff { border-left-color: #9b59b6; }
.event-time {
  color: var(--muted, #999);
  font-size: 11px;
}
.event-payload {
  width: 100%;
  margin: 2px 0 0 0;
  padding: 4px 6px;
  background: var(--surface, #f8f8f8);
  border-radius: 3px;
  font-size: 12px;
  max-height: 120px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
