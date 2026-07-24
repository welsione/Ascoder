<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshCw, BotMessageSquare, Play, Eye, CircleCheck, Clock } from 'lucide-vue-next'
import { useAgentStore } from '../../stores/agent'
import { useAgentToolStore } from '../../stores/agentTool'
import { useSkillStore } from '../../stores/skill'
import { useMcpServerStore } from '../../stores/mcpServer'
import { useLlmProviderStore } from '../../stores/llmProvider'
import * as api from '../../services/agentApi'
import type { AgentConfig, AgentRuntimeStatus, AgentRunRecord, TestRenderResponse } from '../../types/agent'
import AgentEventList from './AgentEventList.vue'

const agentStore = useAgentStore()
const toolStore = useAgentToolStore()
const skillStore = useSkillStore()
const mcpStore = useMcpServerStore()
const llmProviderStore = useLlmProviderStore()

// 观测面板状态
const drawerOpen = ref(false)
const drawerAgent = ref<AgentConfig | null>(null)
const drawerMode = ref<'runs' | 'live'>('runs')
const selectedRunId = ref<number | null>(null)
let livePollTimer: ReturnType<typeof setInterval> | null = null
const now = ref(Date.now())
let nowTimer: ReturnType<typeof setInterval> | null = null

onUnmounted(() => {
  agentStore.unsubscribeStatus()
  if (livePollTimer) clearInterval(livePollTimer)
  if (nowTimer) clearInterval(nowTimer)
})

onMounted(() => {
  nowTimer = setInterval(() => { now.value = Date.now() }, 1000)
  agentStore.fetch()
  llmProviderStore.fetchProviders()
  toolStore.fetch()
  skillStore.fetch()
  mcpStore.fetch()
  // SSE 不可用时由 store 拉一次状态快照兜底，30s 间隔
  livePollTimer = setInterval(() => {
    if (!drawerOpen.value) {
      api.listStatuses().then((views) => {
        const statusMap: Record<string, AgentRuntimeStatus> = {}
        const startedMap: Record<string, string | null> = {}
        for (const v of views) {
          statusMap[v.agentId] = v.status
          startedMap[v.agentId] = v.startedAt
        }
        agentStore.runtimeStatuses = { ...agentStore.runtimeStatuses, ...statusMap }
        agentStore.runtimeStartedAt = { ...agentStore.runtimeStartedAt, ...startedMap }
      }).catch(() => {})
    }
  }, 30_000)
})

function runningSeconds(startedAt: string | null | undefined): number {
  if (!startedAt) return 0
  return Math.max(0, Math.floor((now.value - new Date(startedAt).getTime()) / 1000))
}

function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString('zh-CN', { hour12: false })
}

async function openRunsDrawer(config: AgentConfig) {
  drawerAgent.value = config
  drawerMode.value = 'runs'
  selectedRunId.value = null
  drawerOpen.value = true
  await agentStore.fetchRuns(config.agentId, 0, 10)
  await agentStore.fetchLastRun(config.agentId)
}

async function openLiveDrawer(config: AgentConfig) {
  drawerAgent.value = config
  drawerMode.value = 'live'
  drawerOpen.value = true
  // SSE 订阅（单 agent 订阅，简化处理）
  agentStore.subscribeStatus(config.agentId)
}

function closeDrawer() {
  drawerOpen.value = false
  agentStore.unsubscribeStatus()
}

async function selectRun(run: AgentRunRecord) {
  selectedRunId.value = run.id
}

const activeView = ref<'form' | 'runs'>('form')
const showForm = ref(false)
const editingId = ref<number | null>(null)
const renderResult = ref<TestRenderResponse | null>(null)

onUnmounted(() => agentStore.unsubscribeStatus())

function openCreate() {
  agentStore.resetForm()
  editingId.value = null
  showForm.value = true
  renderResult.value = null
}

function openEdit(config: AgentConfig) {
  editingId.value = config.id
  agentStore.form.agentId = config.agentId
  agentStore.form.displayName = config.displayName
  agentStore.form.description = config.description ?? undefined
  agentStore.form.agentRole = config.agentRole
  agentStore.form.taskKind = config.taskKind ?? undefined
  agentStore.form.systemPrompt = config.systemPrompt
  agentStore.form.taskTemplate = config.taskTemplate ?? undefined
  agentStore.form.maxIters = config.maxIters
  agentStore.form.maxTokens = config.maxTokens ?? undefined
  agentStore.form.timeoutSeconds = config.timeoutSeconds ?? undefined
  agentStore.form.modelId = config.modelId ?? undefined
  agentStore.form.roleKeys = config.roleKeys
  agentStore.form.questionKeywords = config.questionKeywords
  agentStore.form.toolGroupKeys = config.toolGroupKeys
  agentStore.form.skillNames = config.skillNames
  agentStore.form.mcpServerNames = config.mcpServerNames
  agentStore.form.llmProviderId = config.llmProviderId ?? null
  agentStore.form.required = config.required
  agentStore.form.enabled = config.enabled
  agentStore.form.handoffTitle = config.handoffTitle ?? undefined
  agentStore.form.handoffDescription = config.handoffDescription ?? undefined
  agentStore.form.returnTitle = config.returnTitle ?? undefined
  agentStore.form.returnDescription = config.returnDescription ?? undefined
  agentStore.form.sortOrder = config.sortOrder
  showForm.value = true
  renderResult.value = null
}

async function handleSubmit() {
  try {
    if (editingId.value) {
      await api.update(editingId.value, agentStore.form)
      ElMessage.success('Agent 配置已更新')
    } else {
      await api.create(agentStore.form)
      ElMessage.success('Agent 已创建')
    }
    showForm.value = false
    await agentStore.fetch()
  } catch {
    // error 已在 store 中
  }
}

async function handleDelete(config: AgentConfig) {
  if (config.builtin) {
    ElMessage.warning('内置 Agent 不可删除')
    return
  }
  await ElMessageBox.confirm(`确认删除 Agent「${config.displayName}」？`, '删除确认', { type: 'warning' })
  try {
    await api.remove(config.id)
    ElMessage.success('已删除')
    await agentStore.fetch()
  } catch {
    // error
  }
}

async function handleToggleEnabled(config: AgentConfig, enabled: boolean) {
  if (!enabled && config.required) {
    try {
      await ElMessageBox.confirm(
        `「${config.displayName}」是必选 Agent，禁用后将不参与问答。确认禁用？`,
        '禁用确认',
        { type: 'warning', confirmButtonText: '确认禁用' }
      )
    } catch {
      return // 取消
    }
  }
  try {
    await api.updateEnabled(config.id, enabled)
    await agentStore.fetch()
  } catch {
    // error
  }
}

async function handleTestRender() {
  if (!agentStore.form.systemPrompt) return
  try {
    renderResult.value = await api.testRender(editingId.value ?? 0, null)
  } catch {
    // error
  }
}

function statusBadge(status: string) {
  return status === 'RUNNING'
    ? { text: '工作中', type: 'danger' as const }
    : { text: '空闲', type: 'success' as const }
}

const roleOptions = [
  { label: '编排（ORCHESTRATOR）', value: 'ORCHESTRATOR' },
  { label: '专家（SPECIALIST）', value: 'SPECIALIST' },
  { label: '规划（PLANNER）— 一期暂不支持', value: 'PLANNER', disabled: true },
  { label: '自学习（SELF_LEARNING）', value: 'SELF_LEARNING' },
]

const taskKindOptions = [
  { label: 'CODE_RESEARCH', value: 'CODE_RESEARCH' },
  { label: 'IMPACT_ANALYSIS', value: 'IMPACT_ANALYSIS' },
  { label: 'PRODUCT_REVIEW', value: 'PRODUCT_REVIEW' },
  { label: 'TEST_REVIEW', value: 'TEST_REVIEW' },
]
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">Agent 列表</p>
        <h2>管理 Agent 定义、提示词与装配配置</h2>
      </div>
      <div style="display:flex;gap:8px;">
        <el-button circle :loading="agentStore.loading" title="刷新" @click="agentStore.fetch">
          <RefreshCw :size="16" :stroke-width="1.8" />
        </el-button>
        <el-button type="primary" @click="openCreate">
          <BotMessageSquare class="button-icon" :size="16" :stroke-width="1.8" />
          新增 Agent
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="!llmProviderStore.hasProviders && !llmProviderStore.loading"
      type="warning"
      title="尚未配置 LLM 供应商"
      description="请先在「模型供应商」设置中添加至少一个供应商，Agent 将无法使用模型。"
      show-icon
      :closable="false"
      style="margin-bottom:12px;"
    />

    <el-table v-loading="agentStore.loading" :data="agentStore.items" empty-text="暂无 Agent">
      <el-table-column label="名称" min-width="160">
        <template #default="{ row }">
          <span style="font-weight:600;">{{ row.displayName }}</span>
          <br><small style="color:var(--muted);">{{ row.agentId }}</small>
        </template>
      </el-table-column>
      <el-table-column label="角色" width="140">
        <template #default="{ row }">
          <el-tag size="small" :type="row.agentRole === 'ORCHESTRATOR' ? 'warning' : 'info'">{{ row.agentRole }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="statusBadge(agentStore.runtimeStatuses[row.agentId] ?? 'IDLE').type">
            {{ statusBadge(agentStore.runtimeStatuses[row.agentId] ?? 'IDLE').text }}
            <template v-if="(agentStore.runtimeStatuses[row.agentId] ?? 'IDLE') === 'RUNNING'">
              ({{ runningSeconds(agentStore.runtimeStartedAt[row.agentId]) }}s)
            </template>
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="taskKind" width="160" prop="taskKind" />
      <el-table-column label="maxIters" width="90" prop="maxIters" />
      <el-table-column label="调用次数" width="90">
        <template #default="{ row }">
          {{ agentStore.runCounts[row.agentId] ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column label="最近运行" width="170">
        <template #default="{ row }">
          {{ formatTime(agentStore.lastRunAt[row.agentId]) }}
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" @change="handleToggleEnabled(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="内置" width="60">
        <template #default="{ row }">
          <el-tag v-if="row.builtin" size="small" type="info">是</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEdit(row)">编辑</el-button>
          <el-button text size="small" @click="openRunsDrawer(row)">
            <Eye class="button-icon" :size="14" :stroke-width="1.8" />查看
          </el-button>
          <el-button text size="small" @click="openLiveDrawer(row)">
            <CircleCheck class="button-icon" :size="14" :stroke-width="1.8" />实时
          </el-button>
          <el-button text size="small" :disabled="row.builtin" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert v-if="agentStore.error" type="error" :title="agentStore.error" show-icon :closable="false" />
  </section>

  <!-- 观测抽屉 -->
  <el-drawer
    v-model="drawerOpen"
    :title="drawerAgent ? `${drawerAgent.displayName} (${drawerMode === 'runs' ? '历史运行' : '实时流'})` : ''"
    direction="rtl"
    size="640px"
    :before-close="closeDrawer"
  >
    <template v-if="drawerAgent && drawerMode === 'runs'">
      <h4>运行历史（共 {{ agentStore.runsTotal }} 条）</h4>
      <el-table :data="agentStore.runs" size="small" empty-text="暂无运行记录" @row-click="selectRun">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="questionId" label="问题ID" width="90" />
        <el-table-column prop="startedAt" label="开始" />
        <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
      </el-table>
      <div v-if="selectedRunId" style="margin-top:12px;">
        <h4>单条详情</h4>
        <pre style="background:var(--surface-soft);padding:8px;border-radius:6px;white-space:pre-wrap;max-height:200px;overflow:auto;">
{{ ((agentStore.runs.find(r => r.id === selectedRunId)?.inputSummary) || '') + '\n---\n' + ((agentStore.runs.find(r => r.id === selectedRunId)?.outputSummary) || '') }}
        </pre>
        <template v-if="agentStore.runs.find(r => r.id === selectedRunId)?.questionId">
          <el-divider />
          <p class="field-section-title">事件流回看</p>
          <AgentEventList :question-id="agentStore.runs.find(r => r.id === selectedRunId)!.questionId!" :auto-poll="false" />
        </template>
        <template v-else>
          <el-divider />
          <el-button disabled>该记录无关联问题</el-button>
        </template>
      </div>
    </template>
    <template v-else-if="drawerAgent && drawerMode === 'live'">
      <el-alert type="info" :closable="false" style="margin-bottom:8px;">
        SSE 状态流已订阅：<code>/api/agents/{{ drawerAgent.agentId }}/status</code>
      </el-alert>
      <p>当前状态：<el-tag :type="statusBadge(agentStore.runtimeStatuses[drawerAgent.agentId] || 'IDLE').type">
        {{ statusBadge(agentStore.runtimeStatuses[drawerAgent.agentId] || 'IDLE').text }}
      </el-tag></p>
      <p v-if="(agentStore.runtimeStatuses[drawerAgent.agentId] || 'IDLE') === 'RUNNING'">
        进行中：{{ runningSeconds(agentStore.runtimeStartedAt[drawerAgent.agentId]) }}s
      </p>
      <p v-if="agentStore.runtimeQuestionIds[drawerAgent.agentId]">
        关联问题：#{{ agentStore.runtimeQuestionIds[drawerAgent.agentId] }}
      </p>
      <el-divider />
      <template v-if="(agentStore.runtimeStatuses[drawerAgent.agentId] || 'IDLE') === 'RUNNING' && agentStore.runtimeQuestionIds[drawerAgent.agentId]">
        <p class="field-section-title">实时事件流</p>
        <AgentEventList :question-id="agentStore.runtimeQuestionIds[drawerAgent.agentId]!" :auto-poll="true" />
      </template>
      <p v-else style="color:var(--muted);">当前空闲</p>
    </template>
  </el-drawer>

  <!-- 配置表单 -->
  <section v-if="showForm" class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">{{ editingId ? '编辑 Agent' : '新增 Agent' }}</p>
        <h2>配置 Agent 定义、提示词与装配</h2>
      </div>
    </div>

    <!-- 基础信息 -->
    <p class="field-section-title">基础信息</p>
    <div class="settings-form-grid">
      <div>
        <label class="field-label">Agent ID</label>
        <el-input v-model="agentStore.form.agentId" placeholder="code-researcher" :disabled="!!editingId" />
      </div>
      <div>
        <label class="field-label">显示名</label>
        <el-input v-model="agentStore.form.displayName" placeholder="Code Researcher" />
      </div>
      <div>
        <label class="field-label">角色</label>
        <el-select v-model="agentStore.form.agentRole" style="width:100%;">
          <el-option v-for="opt in roleOptions" :key="opt.value" :label="opt.label" :value="opt.value" :disabled="opt.disabled" />
        </el-select>
      </div>
      <div v-if="agentStore.form.agentRole === 'SPECIALIST'">
        <label class="field-label">taskKind</label>
        <el-select v-model="agentStore.form.taskKind" style="width:100%;" clearable placeholder="选择输入依赖">
          <el-option v-for="opt in taskKindOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
      </div>
      <div>
        <label class="field-label">排序</label>
        <el-input-number v-model="agentStore.form.sortOrder" :min="0" :max="99" style="width:100%;" />
      </div>
      <div>
        <label class="field-label">启用</label>
        <div class="switch-wrap"><el-switch v-model="agentStore.form.enabled" /></div>
      </div>
      <div v-if="agentStore.form.agentRole !== 'SELF_LEARNING'">
        <label class="field-label">必选</label>
        <div class="switch-wrap"><el-switch v-model="agentStore.form.required" /></div>
      </div>
    </div>

    <!-- 提示词 -->
    <p class="field-section-title">提示词</p>
    <div class="settings-form-grid">
      <div class="span-4">
        <label class="field-label">系统提示词</label>
        <el-input v-model="agentStore.form.systemPrompt" type="textarea" :rows="8" placeholder="Agent 角色与行为约束" />
      </div>
      <div class="span-3">
        <label class="field-label">任务模板（{{}} 模板语法）</label>
        <el-input v-model="agentStore.form.taskTemplate" type="textarea" :rows="6" placeholder="SPECIALIST 必填，ORCHESTRATOR 可选" />
      </div>
      <div style="align-self:end;">
        <el-button @click="handleTestRender">渲染预览</el-button>
      </div>
    </div>
    <el-alert v-if="renderResult" type="info" :closable="false" style="margin-top:8px;">
      <template #title>渲染预览</template>
      <pre style="white-space:pre-wrap;max-height:200px;overflow:auto;">{{ renderResult.renderedText }}</pre>
      <p v-if="renderResult.warnings.length" style="color:var(--warning);margin-top:4px;">
        ⚠️ {{ renderResult.warnings.join('; ') }}
      </p>
    </el-alert>

    <!-- 模型参数 -->
    <p class="field-section-title">模型参数（留空用全局默认）</p>
    <div class="settings-form-grid">
      <div>
        <label class="field-label">供应商</label>
        <el-select v-model="agentStore.form.llmProviderId" style="width:100%;" clearable placeholder="默认供应商">
          <el-option :label="'默认供应商'" :value="null" />
          <el-option
            v-for="p in llmProviderStore.enabledProviders"
            :key="p.id"
            :label="`${p.name} (${p.modelId})`"
            :value="p.id"
          />
        </el-select>
      </div>
      <div>
        <label class="field-label">modelId</label>
        <el-input
          v-model="agentStore.form.modelId"
          :placeholder="agentStore.form.llmProviderId ? (llmProviderStore.enabledProviders.find(p => p.id === agentStore.form.llmProviderId)?.modelId ?? 'MiniMax-M2.7') : 'MiniMax-M2.7'"
        />
      </div>
      <div>
        <label class="field-label">maxIters</label>
        <el-input-number v-model="agentStore.form.maxIters" :min="1" :max="999" style="width:100%;" />
      </div>
      <div>
        <label class="field-label">maxTokens</label>
        <el-input-number
          v-model="agentStore.form.maxTokens"
          :min="1"
          :max="999999"
          style="width:100%;"
          :placeholder="agentStore.form.llmProviderId ? String(llmProviderStore.enabledProviders.find(p => p.id === agentStore.form.llmProviderId)?.maxTokens ?? '') : ''"
        />
      </div>
      <div>
        <label class="field-label">timeoutSeconds</label>
        <el-input-number
          v-model="agentStore.form.timeoutSeconds"
          :min="1"
          :max="3600"
          style="width:100%;"
          :placeholder="agentStore.form.llmProviderId ? String(llmProviderStore.enabledProviders.find(p => p.id === agentStore.form.llmProviderId)?.timeoutSeconds ?? '') : ''"
        />
      </div>
    </div>

    <!-- 触发条件 -->
    <template v-if="agentStore.form.agentRole !== 'SELF_LEARNING'">
    <p class="field-section-title">触发条件（SPECIALIST）</p>
    <div class="settings-form-grid">
      <div class="span-2">
        <label class="field-label">角色关键词</label>
        <el-select v-model="agentStore.form.roleKeys" multiple filterable allow-create collapse-tags style="width:100%;" placeholder="输入后回车添加，如 tester">
          <el-option v-for="r in ['tester','developer','product_manager']" :key="r" :label="r" :value="r" />
        </el-select>
      </div>
      <div class="span-2">
        <label class="field-label">问题关键词</label>
        <el-select v-model="agentStore.form.questionKeywords" multiple filterable allow-create collapse-tags style="width:100%;" placeholder="输入后回车添加，如 影响">
        </el-select>
      </div>
    </div>
    </template>

    <!-- 工具装配 -->
    <template v-if="agentStore.form.agentRole !== 'SELF_LEARNING'">
    <p class="field-section-title">工具装配</p>
    <div class="settings-form-grid">
      <div class="span-2">
        <label class="field-label">工具组</label>
        <el-select v-model="agentStore.form.toolGroupKeys" multiple collapse-tags style="width:100%;" placeholder="选择可用工具组">
          <el-option v-for="t in toolStore.tools" :key="t.toolKey" :label="`${t.displayName} (${t.toolKey})`" :value="t.toolKey" />
        </el-select>
      </div>
      <div>
        <label class="field-label">Skill</label>
        <el-select v-model="agentStore.form.skillNames" multiple collapse-tags style="width:100%;" placeholder="选择 Skill">
          <el-option v-for="s in skillStore.skills" :key="s.name" :label="s.name" :value="s.name" />
        </el-select>
      </div>
      <div>
        <label class="field-label">MCP 服务器</label>
        <el-select v-model="agentStore.form.mcpServerNames" multiple collapse-tags style="width:100%;" placeholder="选择 MCP">
          <el-option v-for="m in mcpStore.servers" :key="m.name" :label="m.name" :value="m.name" />
        </el-select>
      </div>
    </div>
    </template>

    <!-- 委派描述 -->
    <template v-if="agentStore.form.agentRole !== 'SELF_LEARNING'">
    <p class="field-section-title">委派描述</p>
    <div class="settings-form-grid">
      <div>
        <label class="field-label">委派标题</label>
        <el-input v-model="agentStore.form.handoffTitle" placeholder="任务委派" />
      </div>
      <div class="span-3">
        <label class="field-label">委派描述</label>
        <el-input v-model="agentStore.form.handoffDescription" type="textarea" :rows="2" />
      </div>
      <div>
        <label class="field-label">回传标题</label>
        <el-input v-model="agentStore.form.returnTitle" placeholder="证据回传" />
      </div>
      <div class="span-3">
        <label class="field-label">回传描述</label>
        <el-input v-model="agentStore.form.returnDescription" type="textarea" :rows="2" />
      </div>
    </div>
    </template>

    <div class="settings-actions">
      <el-button @click="showForm=false">取消</el-button>
      <el-button type="primary" :loading="agentStore.createLoading" @click="handleSubmit">
        {{ editingId ? '保存' : '创建' }}
      </el-button>
    </div>

    <el-alert v-if="agentStore.error" type="error" :title="agentStore.error" show-icon :closable="false" />
  </section>
</template>

<style scoped>
.field-section-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin: var(--spacing-5) 0 var(--spacing-2);
}
</style>
