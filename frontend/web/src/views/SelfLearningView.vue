<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Bot, BrainCircuit, DatabaseZap, FileCheck2, History, ShieldCheck, Sparkles, Trash2 } from 'lucide-vue-next'
import * as api from '../services/selfLearningApi'
import { useProjectSpaceStore } from '../stores/projectSpace'
import { useAgentRunPolling } from '../composables/useAgentRunPolling'
import { agentRunStatusLabel, agentRunStatusType, compactText } from '../utils/selfLearningRender'
import RawEventsSection from '../components/selflearning/RawEventsSection.vue'
import InsightsSection from '../components/selflearning/InsightsSection.vue'
import KnowledgeSection from '../components/selflearning/KnowledgeSection.vue'
import type {
  SelfLearningAgentRun,
  SelfLearningSettings,
  SelfLearningSummary,
} from '../types/selfLearning'

type TabName = 'raw' | 'insights' | 'knowledge'

const route = useRoute()
const router = useRouter()
const projectSpaceStore = useProjectSpaceStore()
const projectSpaceId = computed(() => Number(route.params.projectSpaceId))
const space = computed(() => projectSpaceStore.spaces.find((item) => item.id === projectSpaceId.value) ?? null)

const loading = ref(false)
const runningAgent = ref(false)
const importingHistory = ref(false)
const cleaningLegacyInsights = ref(false)
const activeTab = ref<TabName>('insights')
const summary = ref<SelfLearningSummary | null>(null)
const settings = ref<SelfLearningSettings | null>(null)
const lastAgentRun = ref<SelfLearningAgentRun | null>(null)
const activeKnowledgeCount = ref(0)
const refreshToken = ref(0)

const { agentRuns, fetchRuns, startPolling } = useAgentRunPolling()

async function loadAll() {
  if (!projectSpaceId.value || Number.isNaN(projectSpaceId.value)) return
  loading.value = true
  try {
    if (!projectSpaceStore.spaces.length) {
      await projectSpaceStore.fetch()
    }
    const [nextSummary, knowledgeItems] = await Promise.all([
      api.getSummary(projectSpaceId.value),
      api.listKnowledgeItems(projectSpaceId.value),
    ])
    summary.value = nextSummary
    settings.value = nextSummary.settings
    await fetchRuns(projectSpaceId.value)
    activeKnowledgeCount.value = knowledgeItems.filter(
      (item) => item.status === 'ACTIVE' || item.status === 'VERIFIED',
    ).length
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载自学习数据失败')
  } finally {
    loading.value = false
  }
}

async function reloadSummary() {
  summary.value = await api.getSummary(projectSpaceId.value)
  settings.value = summary.value.settings
}

/** 子组件数据变化后：刷新共享摘要并通知各子组件重载各自列表。 */
function onSectionChanged() {
  refreshToken.value++
  reloadSummary().catch(() => undefined)
}

async function updateSettings(patch: Partial<SelfLearningSettings>) {
  if (!settings.value) return
  try {
    settings.value = await api.updateSettings(projectSpaceId.value, patch)
    await reloadSummary()
    ElMessage.success('自学习设置已更新')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '更新自学习设置失败')
    await loadAll()
  }
}

function updateSettingFlag(key: keyof SelfLearningSettings, value: boolean | string | number) {
  updateSettings({ [key]: Boolean(value) })
}

async function runAgent() {
  if (!settings.value?.enabled) {
    ElMessage.warning('请先开启自学习功能')
    return
  }
  runningAgent.value = true
  try {
    lastAgentRun.value = await api.runSelfLearningAgent(projectSpaceId.value)
    activeTab.value = 'insights'
    await reloadSummary()
    ElMessage.success(lastAgentRun.value.message)
    if (lastAgentRun.value.status === 'QUEUED' || lastAgentRun.value.status === 'RUNNING') {
      startPolling(projectSpaceId.value, async (runs) => {
        await loadAll()
        ElMessage.success(runs[0]?.message ?? 'Self Learning Agent 整理完成')
      })
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '运行 Self Learning Agent 失败')
  } finally {
    runningAgent.value = false
  }
}

async function importHistory() {
  if (!settings.value?.enabled) {
    ElMessage.warning('请先开启自学习功能')
    return
  }
  if (!settings.value.rawEventCaptureEnabled) {
    ElMessage.warning('请先开启原始事件记录')
    return
  }
  importingHistory.value = true
  try {
    const result = await api.importHistoryRawEvents(projectSpaceId.value)
    activeTab.value = 'raw'
    onSectionChanged()
    ElMessage.success(`${result.message} 已跳过 ${result.skippedRawEventCount} 条已有记录。`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '导入历史聊天失败')
  } finally {
    importingHistory.value = false
  }
}

async function cleanupLegacyInsights() {
  try {
    await ElMessageBox.confirm(
      '将删除旧版 Self Learning Agent 生成的待审核洞察，保留原始 conversation 记录，之后可用新版后台整理重新生成。确认清理吗？',
      '清理旧版候选洞察',
      { type: 'warning' }
    )
  } catch {
    return
  }
  cleaningLegacyInsights.value = true
  try {
    const result = await api.cleanupLegacyInsights(projectSpaceId.value)
    ElMessage.success(result.message)
    onSectionChanged()
    await loadAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '清理旧版候选洞察失败')
  } finally {
    cleaningLegacyInsights.value = false
  }
}

onMounted(loadAll)
</script>

<template>
  <section class="learning-page" v-loading="loading">
    <div class="learning-nav">
      <el-button text @click="router.back()">
        <ArrowLeft class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        返回
      </el-button>
    </div>

    <section class="learning-hero">
      <div class="hero-copy">
        <div class="hero-eyebrow">
          <BrainCircuit :size="18" :stroke-width="2" />
          项目空间自学习
        </div>
        <h1>{{ space?.name ?? '自学习管理' }}</h1>
        <p>
          先完整记录，再由 Self Learning Agent 整理候选洞察，管理员审核后才归纳为正式知识。
          正式知识只作为回答线索，当前代码和 Git 证据始终优先。
        </p>
      </div>
      <div class="hero-switch-panel">
        <div>
          <span>自学习状态</span>
          <strong>{{ settings?.enabled ? '已开启' : '已关闭' }}</strong>
        </div>
        <el-switch
          :model-value="settings?.enabled"
          size="large"
          @change="(value: boolean | string | number) => updateSettingFlag('enabled', value)"
        />
      </div>
    </section>

    <section class="metric-grid">
      <article class="metric-card">
        <DatabaseZap :size="20" :stroke-width="2" />
        <span>原始记录</span>
        <strong>{{ summary?.rawEventCount ?? 0 }}</strong>
        <p>问答、工具调用、反馈和证据留痕</p>
      </article>
      <article class="metric-card highlight">
        <Sparkles :size="20" :stroke-width="2" />
        <span>待审核洞察</span>
        <strong>{{ summary?.pendingInsightCount ?? 0 }}</strong>
        <p>Self Learning Agent 整理后的草稿</p>
      </article>
      <article class="metric-card">
        <FileCheck2 :size="20" :stroke-width="2" />
        <span>正式知识</span>
        <strong>{{ summary?.knowledgeItemCount ?? 0 }}</strong>
        <p>{{ activeKnowledgeCount }} 条可作为回答线索</p>
      </article>
      <article class="metric-card">
        <ShieldCheck :size="20" :stroke-width="2" />
        <span>治理状态</span>
        <strong>{{ settings?.adminReviewRequired ? '人审' : '自动' }}</strong>
        <p>推荐保持管理员审核</p>
      </article>
    </section>

    <section class="policy-panel">
      <div class="policy-copy">
        <h2>学习策略</h2>
        <p>关闭自学习后，不再写入新的学习记录；已有记录、洞察和正式知识仍可管理。</p>
        <div class="agent-run-panel">
          <div>
            <strong>Self Learning Agent</strong>
            <span>后台从最近原始记录整理候选洞察，完成后等待管理员审核。</span>
          </div>
          <div class="agent-run-actions">
            <el-button :loading="importingHistory" @click="importHistory">
              <History class="button-icon" :size="16" :stroke-width="1.8" />
              导入历史聊天
            </el-button>
            <el-button type="primary" :loading="runningAgent" @click="runAgent">
              <Bot class="button-icon" :size="16" :stroke-width="1.8" />
              后台整理
            </el-button>
            <el-button type="danger" plain :loading="cleaningLegacyInsights" @click="cleanupLegacyInsights">
              <Trash2 class="button-icon" :size="16" :stroke-width="1.8" />
              清理旧洞察
            </el-button>
          </div>
        </div>
        <div v-if="lastAgentRun" class="agent-run-result">
          <span>{{ lastAgentRun.message }}</span>
          <strong>{{ lastAgentRun.createdInsightCount }} 洞察 / {{ lastAgentRun.consumedRawEventCount }} 记录</strong>
        </div>
        <div v-if="agentRuns.length" class="agent-run-history">
          <div v-for="run in agentRuns.slice(0, 5)" :key="run.id" class="agent-run-history-item">
            <div>
              <strong>#{{ run.id }} {{ agentRunStatusLabel(run.status) }}</strong>
              <span>{{ run.message || run.errorMessage || '暂无运行消息' }}</span>
              <span v-if="run.failureDetailsJson">失败详情：{{ compactText(run.failureDetailsJson, '') }}</span>
            </div>
            <div class="agent-run-history-meta">
              <el-tag size="small" :type="agentRunStatusType(run.status)">{{ agentRunStatusLabel(run.status) }}</el-tag>
              <span>{{ run.createdInsightCount }} 洞察 / {{ run.consumedRawEventCount }} 记录</span>
              <span v-if="run.failedConversationCount">{{ run.failedConversationCount }} 失败</span>
            </div>
          </div>
        </div>
      </div>
      <div class="policy-switches">
        <label>
          <span>记录原始事件</span>
          <el-switch
            :model-value="settings?.rawEventCaptureEnabled"
            @change="(value: boolean | string | number) => updateSettingFlag('rawEventCaptureEnabled', value)"
          />
        </label>
        <label>
          <span>自动生成候选洞察</span>
          <el-switch
            :model-value="settings?.autoInsightEnabled"
            @change="(value: boolean | string | number) => updateSettingFlag('autoInsightEnabled', value)"
          />
        </label>
        <label>
          <span>回答时使用正式知识</span>
          <el-switch
            :model-value="settings?.answerInjectionEnabled"
            @change="(value: boolean | string | number) => updateSettingFlag('answerInjectionEnabled', value)"
          />
        </label>
        <label>
          <span>管理员审核必需</span>
          <el-switch
            :model-value="settings?.adminReviewRequired"
            @change="(value: boolean | string | number) => updateSettingFlag('adminReviewRequired', value)"
          />
        </label>
      </div>
    </section>

    <section class="learning-workbench">
      <el-tabs v-model="activeTab" class="learning-tabs">
        <el-tab-pane label="原始记录" name="raw">
          <RawEventsSection :project-space-id="projectSpaceId" :refresh-token="refreshToken" @changed="onSectionChanged" />
        </el-tab-pane>
        <el-tab-pane label="待审核洞察" name="insights">
          <InsightsSection :project-space-id="projectSpaceId" :refresh-token="refreshToken" @changed="onSectionChanged" />
        </el-tab-pane>
        <el-tab-pane label="正式知识" name="knowledge">
          <KnowledgeSection :project-space-id="projectSpaceId" :refresh-token="refreshToken" @changed="onSectionChanged" />
        </el-tab-pane>
      </el-tabs>
    </section>
  </section>
</template>

<style scoped>
.learning-page {
  width: min(var(--page-max-width), calc(100vw - 48px));
  margin: 0 auto;
  padding: var(--page-padding-y) 0 var(--spacing-10);
}

.learning-nav {
  margin-bottom: var(--spacing-4);
}

.learning-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: var(--spacing-6);
  align-items: stretch;
  padding: var(--spacing-7);
  border: 1px solid rgba(79, 110, 247, 0.14);
  border-radius: var(--radius-xl);
  background:
    linear-gradient(135deg, rgba(79, 110, 247, 0.1), transparent 38%),
    linear-gradient(145deg, rgba(18, 24, 46, 0.04), transparent),
    var(--surface);
  box-shadow: var(--shadow-soft);
}

.hero-copy h1 {
  margin: var(--spacing-2) 0;
  font-size: clamp(2rem, 3vw, 3.2rem);
  line-height: 1.05;
  letter-spacing: 0;
}

.hero-copy p {
  max-width: 760px;
  margin: 0;
  color: var(--muted);
  line-height: 1.8;
}

.hero-eyebrow {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  color: var(--chat-accent);
  font-weight: var(--font-weight-semibold);
}

.hero-switch-panel,
.metric-card,
.policy-panel,
.learning-workbench {
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface);
}

.hero-switch-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  padding: var(--spacing-5);
}

.hero-switch-panel span,
.metric-card span {
  display: block;
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.hero-switch-panel strong,
.metric-card strong {
  display: block;
  margin-top: var(--spacing-1);
  font-size: var(--font-size-2xl);
}

.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--spacing-4);
  margin-top: var(--spacing-5);
}

.metric-card {
  padding: var(--spacing-5);
}

.metric-card svg {
  color: var(--chat-accent);
}

.metric-card.highlight {
  border-color: rgba(245, 158, 11, 0.24);
  background: linear-gradient(135deg, rgba(245, 158, 11, 0.08), transparent 42%), var(--surface);
}

.metric-card p,
.policy-copy p,
.section-heading p {
  margin: var(--spacing-2) 0 0;
  color: var(--muted);
  line-height: 1.6;
}

.policy-panel {
  display: flex;
  justify-content: space-between;
  gap: var(--spacing-6);
  margin-top: var(--spacing-5);
  padding: var(--spacing-5);
}

.policy-copy h2 {
  margin: 0;
}

.agent-run-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-top: var(--spacing-4);
  padding: var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.16);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(79, 110, 247, 0.08), transparent 56%), var(--surface-muted);
}

.agent-run-panel strong,
.agent-run-panel span {
  display: block;
}

.agent-run-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--spacing-2);
}

.agent-run-panel span,
.agent-run-result span {
  margin-top: var(--spacing-1);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.agent-run-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  margin-top: var(--spacing-3);
  padding: 0 var(--spacing-1);
}

.agent-run-history {
  display: grid;
  gap: var(--spacing-2);
  margin-top: var(--spacing-4);
}

.agent-run-history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.agent-run-history-item strong,
.agent-run-history-item span {
  display: block;
}

.agent-run-history-item span,
.agent-run-history-meta {
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.agent-run-history-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: var(--spacing-2);
}

.policy-switches {
  display: grid;
  grid-template-columns: repeat(2, minmax(210px, 1fr));
  gap: var(--spacing-3);
}

.policy-switches label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.learning-workbench {
  margin-top: var(--spacing-5);
  padding: var(--spacing-5);
}

.section-heading {
  margin-bottom: var(--spacing-4);
}

@media (max-width: 980px) {
  .learning-page {
    width: min(100% - 28px, 760px);
  }

  .learning-hero,
  .policy-panel {
    grid-template-columns: 1fr;
    flex-direction: column;
  }

  .metric-grid,
  .policy-switches {
    grid-template-columns: 1fr;
  }

  .section-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .agent-run-panel,
  .agent-run-result,
  .agent-run-history-item {
    align-items: flex-start;
    flex-direction: column;
  }

  .agent-run-actions {
    justify-content: flex-start;
  }
}
</style>
