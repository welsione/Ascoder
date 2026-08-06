<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { History } from 'lucide-vue-next'
import * as api from '../../services/selfLearningApi'
import { formatTime } from '../../utils/format'
import type { LearningRawEvent } from '../../types/selfLearning'

const props = defineProps<{
  projectSpaceId: number
  refreshToken: number
}>()

const emit = defineEmits<{
  changed: []
}>()

const rawEvents = ref<LearningRawEvent[]>([])
const loading = ref(false)
const importingHistory = ref(false)
const cleaningLegacy = ref(false)

async function loadRawEvents() {
  loading.value = true
  try {
    rawEvents.value = await api.listRawEvents(props.projectSpaceId)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载原始记录失败')
  } finally {
    loading.value = false
  }
}

async function importHistory() {
  importingHistory.value = true
  try {
    const result = await api.importHistoryRawEvents(props.projectSpaceId)
    await loadRawEvents()
    ElMessage.success(`${result.message} 已跳过 ${result.skippedRawEventCount} 条已有记录。`)
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '导入历史聊天失败')
  } finally {
    importingHistory.value = false
  }
}

async function cleanupLegacyRawEvents() {
  try {
    await ElMessageBox.confirm(
      '将删除旧粒度的 USER_QUESTION / QUERY_PLAN / ASSISTANT_ANSWER 原始记录；关联的未审核洞察会删除，已生成正式知识的内容会标记为待复核。确认继续？',
      '清理旧粒度记录',
      { type: 'warning' }
    )
  } catch {
    return
  }
  cleaningLegacy.value = true
  try {
    const result = await api.cleanupLegacyRawEvents(props.projectSpaceId)
    await loadRawEvents()
    ElMessage.success(
      `${result.message} 删除洞察 ${result.deletedInsightCount} 条，标记待复核知识 ${result.staleKnowledgeItemCount} 条。`
    )
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '清理旧粒度记录失败')
  } finally {
    cleaningLegacy.value = false
  }
}

onMounted(loadRawEvents)
watch(() => props.refreshToken, loadRawEvents)
</script>

<template>
  <div v-loading="loading" class="raw-events-section">
    <div class="section-heading">
      <div>
        <p class="kicker">原始记录</p>
        <h2>Raw Events</h2>
        <p>只做事实留痕，不直接参与回答召回。历史聊天可手动导入，重复导入会自动跳过。</p>
      </div>
      <div class="section-actions">
        <el-button type="danger" plain :loading="cleaningLegacy" @click="cleanupLegacyRawEvents">
          清理旧粒度记录
        </el-button>
        <el-button :loading="importingHistory" @click="importHistory">
          <History class="button-icon" :size="16" :stroke-width="1.8" />
          导入历史聊天
        </el-button>
      </div>
    </div>
    <div class="event-list">
      <article v-for="event in rawEvents" :key="event.id" class="event-card">
        <div class="event-head">
          <el-tag size="small" effect="plain">{{ event.eventType }}</el-tag>
          <span>{{ formatTime(event.createdAt, '未记录') }}</span>
        </div>
        <h4>{{ event.summary || '未提供摘要' }}</h4>
        <p>Agent：{{ event.agentId || 'system' }} · Question #{{ event.questionId || '-' }}</p>
      </article>
      <div v-if="!rawEvents.length" class="empty-box compact-empty">
        <p>还没有原始记录。开启自学习并完成问答后会自动沉淀。</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.event-list {
  display: grid;
  gap: var(--spacing-3);
}

.event-card {
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface-muted);
}

.event-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-2);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.event-card h4 {
  margin: var(--spacing-3) 0 0;
}

.event-card p {
  margin-top: var(--spacing-2);
  color: var(--muted);
  font-size: var(--font-size-sm);
}
</style>
