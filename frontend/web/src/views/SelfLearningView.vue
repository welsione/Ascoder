<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Bot, BrainCircuit, CheckCircle2, DatabaseZap, FileCheck2, GitBranch, History, Plus, ShieldCheck, Sparkles, Trash2 } from 'lucide-vue-next'
import * as api from '../services/selfLearningApi'
import { useProjectSpaceStore } from '../stores/projectSpace'
import type {
  LearningInsight,
  LearningInsightVerification,
  LearningInsightVerificationStatus,
  LearningInsightStatus,
  LearningAgentRunRecord,
  LearningAgentRunStatus,
  LearningKnowledgeItem,
  LearningKnowledgeStatus,
  LearningKnowledgeType,
  LearningRawEvent,
  SaveLearningInsightPayload,
  SaveLearningKnowledgeItemPayload,
  SelfLearningAgentRun,
  SelfLearningSettings,
  SelfLearningSummary,
} from '../types/selfLearning'

type TabName = 'raw' | 'insights' | 'knowledge'
type RefineChatMessage = {
  role: 'user' | 'assistant'
  content: string
}

type JsonObject = Record<string, unknown>

const route = useRoute()
const router = useRouter()
const projectSpaceStore = useProjectSpaceStore()
const projectSpaceId = computed(() => Number(route.params.projectSpaceId))
const space = computed(() => projectSpaceStore.spaces.find((item) => item.id === projectSpaceId.value) ?? null)

const loading = ref(false)
const saving = ref(false)
const runningAgent = ref(false)
const importingHistory = ref(false)
const cleaningLegacy = ref(false)
const cleaningLegacyInsights = ref(false)
const verifyingInsight = ref(false)
const refiningInsight = ref(false)
const activeTab = ref<TabName>('insights')
const insightStatusFilter = ref<LearningInsightStatus | ''>('')
const knowledgeStatusFilter = ref<LearningKnowledgeStatus | ''>('')
const summary = ref<SelfLearningSummary | null>(null)
const settings = ref<SelfLearningSettings | null>(null)
const rawEvents = ref<LearningRawEvent[]>([])
const insights = ref<LearningInsight[]>([])
const knowledgeItems = ref<LearningKnowledgeItem[]>([])
const agentRuns = ref<LearningAgentRunRecord[]>([])
const lastAgentRun = ref<SelfLearningAgentRun | null>(null)
const selectedInsightId = ref<number | null>(null)
const insightDialogVisible = ref(false)
const knowledgeDialogVisible = ref(false)
const editingInsightId = ref<number | null>(null)
const editingKnowledgeId = ref<number | null>(null)
const verificationResult = ref<LearningInsightVerification | null>(null)
const refineDialogVisible = ref(false)
const refineInstruction = ref('')
const refineSuggestion = ref<SaveLearningInsightPayload | null>(null)
const refineMessages = ref<RefineChatMessage[]>([])

const insightForm = reactive<SaveLearningInsightPayload>({
  type: 'QUESTION_ANSWER',
  status: 'PENDING_REVIEW',
  title: '',
  summary: '',
  conclusion: '',
  businessContext: '',
  glossaryMappingsJson: '',
  codeSymbolsJson: '',
  warnings: '',
  applicableScope: '',
  evidenceJson: '',
  gitProvenanceJson: '',
  tags: '',
  confidence: 0.5,
})

const knowledgeForm = reactive<SaveLearningKnowledgeItemPayload>({
  type: 'QUESTION_ANSWER',
  status: 'VERIFIED',
  title: '',
  content: '',
  summary: '',
  applicableScope: '',
  evidenceJson: '',
  gitProvenanceJson: '',
  tags: '',
  confidence: 0.8,
})

const knowledgeTypeOptions: Array<{ label: string; value: LearningKnowledgeType }> = [
  { label: '业务语境', value: 'BUSINESS_CONTEXT' },
  { label: '代码名词', value: 'GLOSSARY' },
  { label: '代码约定', value: 'CODE_CONVENTION' },
  { label: '排查路径', value: 'TROUBLESHOOTING' },
  { label: '架构决策', value: 'ARCHITECTURE_DECISION' },
  { label: '缺陷修复', value: 'BUG_FIX' },
  { label: '负面案例', value: 'NEGATIVE_EXAMPLE' },
  { label: '问答案例', value: 'QUESTION_ANSWER' },
  { label: '需求逻辑', value: 'REQUIREMENT_LOGIC' },
  { label: '测试考虑', value: 'TEST_CONSIDERATION' },
]

const insightStatusOptions: Array<{ label: string; value: LearningInsightStatus }> = [
  { label: '草稿', value: 'DRAFT' },
  { label: '待审核', value: 'PENDING_REVIEW' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已归纳', value: 'MERGED' },
]

const knowledgeStatusOptions: Array<{ label: string; value: LearningKnowledgeStatus }> = [
  { label: '启用', value: 'ACTIVE' },
  { label: '已验证', value: 'VERIFIED' },
  { label: '待复核', value: 'STALE' },
  { label: '已过期', value: 'DEPRECATED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '防错案例', value: 'NEGATIVE' },
]

const pendingInsights = computed(() => insights.value.filter((item) => item.status === 'PENDING_REVIEW'))
const activeKnowledge = computed(() => knowledgeItems.value.filter((item) => item.status === 'ACTIVE' || item.status === 'VERIFIED'))
const selectedInsight = computed(() => {
  return insights.value.find((item) => item.id === selectedInsightId.value) ?? insights.value[0] ?? null
})

function typeLabel(value: LearningKnowledgeType) {
  return knowledgeTypeOptions.find((item) => item.value === value)?.label ?? value
}

function insightStatusLabel(value: LearningInsightStatus) {
  return insightStatusOptions.find((item) => item.value === value)?.label ?? value
}

function knowledgeStatusLabel(value: LearningKnowledgeStatus) {
  return knowledgeStatusOptions.find((item) => item.value === value)?.label ?? value
}

function insightTagType(value: LearningInsightStatus) {
  if (value === 'PENDING_REVIEW') return 'warning'
  if (value === 'APPROVED' || value === 'MERGED') return 'success'
  if (value === 'REJECTED') return 'danger'
  return 'info'
}

function knowledgeTagType(value: LearningKnowledgeStatus) {
  if (value === 'ACTIVE' || value === 'VERIFIED') return 'success'
  if (value === 'STALE') return 'warning'
  if (value === 'REJECTED' || value === 'NEGATIVE') return 'danger'
  return 'info'
}

function agentRunStatusLabel(value: LearningAgentRunStatus | null) {
  if (value === 'QUEUED') return '排队中'
  if (value === 'RUNNING') return '整理中'
  if (value === 'SUCCEEDED') return '已完成'
  if (value === 'PARTIAL_FAILED') return '部分失败'
  if (value === 'SKIPPED') return '已跳过'
  if (value === 'FAILED') return '失败'
  return '未记录'
}

function agentRunStatusType(value: LearningAgentRunStatus | null) {
  if (value === 'RUNNING' || value === 'QUEUED') return 'warning'
  if (value === 'SUCCEEDED') return 'success'
  if (value === 'FAILED' || value === 'PARTIAL_FAILED') return 'danger'
  return 'info'
}

function verificationStatusLabel(value: LearningInsightVerificationStatus | null | undefined) {
  if (value === 'VERIFIED') return '代码支持'
  if (value === 'NEEDS_CHANGES') return '建议修改'
  if (value === 'INSUFFICIENT_EVIDENCE') return '证据不足'
  if (value === 'CONTRADICTED') return '存在冲突'
  return value || '未复核'
}

function verificationStatusType(value: LearningInsightVerificationStatus | null | undefined) {
  if (value === 'VERIFIED') return 'success'
  if (value === 'NEEDS_CHANGES' || value === 'INSUFFICIENT_EVIDENCE') return 'warning'
  if (value === 'CONTRADICTED') return 'danger'
  return 'info'
}

function formatTime(value: string | null) {
  if (!value) return '未记录'
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function compactText(value: string | null | undefined, fallback = '暂无内容') {
  if (!value || !value.trim()) return fallback
  return value
}

function prettyJson(value: string | null | undefined) {
  if (!value || !value.trim()) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function parseJsonValue(value: string | null | undefined): unknown {
  if (!value || !value.trim()) return null
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}

function readableJsonValue(value: unknown) {
  if (value === null || value === undefined || value === '') return '未提供'
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return String(value)
  return JSON.stringify(value, null, 2)
}

function asObject(value: unknown): JsonObject | null {
  return value && typeof value === 'object' && !Array.isArray(value) ? (value as JsonObject) : null
}

function arrayObjects(value: string | null | undefined) {
  const parsed = parseJsonValue(value)
  if (Array.isArray(parsed)) {
    return parsed.map(asObject).filter((item): item is JsonObject => Boolean(item))
  }
  const object = asObject(parsed)
  return object ? [object] : []
}

function glossaryItems(value: string | null | undefined) {
  return arrayObjects(value).map((item, index) => ({
    key: `glossary-${index}`,
    term: readableJsonValue(item.term ?? item.name ?? item.businessTerm ?? item.word),
    meaning: readableJsonValue(item.meaning ?? item.definition ?? item.description ?? item.value),
    codeSymbol: readableJsonValue(item.codeSymbol ?? item.symbol ?? item.code ?? item.path),
    source: readableJsonValue(item.source ?? item.evidence),
  }))
}

function symbolItems(value: string | null | undefined) {
  const parsed = parseJsonValue(value)
  if (Array.isArray(parsed)) {
    return parsed.map((item, index) => {
      const object = asObject(item)
      return {
        key: `symbol-${index}`,
        symbol: object ? readableJsonValue(object.symbol ?? object.name ?? object.codeSymbol) : readableJsonValue(item),
        kind: object ? readableJsonValue(object.kind ?? object.type) : '',
        file: object ? readableJsonValue(object.file ?? object.path) : '',
      }
    })
  }
  const object = asObject(parsed)
  if (object) {
    return Object.entries(object).map(([key, item]) => ({
      key,
      symbol: key,
      kind: '',
      file: readableJsonValue(item),
    }))
  }
  return []
}

function evidenceItems(value: string | null | undefined) {
  const parsed = parseJsonValue(value)
  const list = Array.isArray(parsed) ? parsed : asObject(parsed) ? Object.entries(parsed as JsonObject).map(([key, item]) => ({ key, value: item })) : []
  return list.map((item, index) => {
    const object = asObject(item)
    return {
      key: `evidence-${index}`,
      title: readableJsonValue(object?.title ?? object?.file ?? object?.path ?? object?.symbol ?? object?.key ?? `证据 ${index + 1}`),
      summary: readableJsonValue(object?.summary ?? object?.description ?? object?.value ?? object?.reason ?? item),
      meta: [
        object?.questionId ? `Question #${readableJsonValue(object.questionId)}` : '',
        object?.rawEventId ? `Raw #${readableJsonValue(object.rawEventId)}` : '',
        object?.tool ? `工具 ${readableJsonValue(object.tool)}` : '',
      ].filter(Boolean),
    }
  })
}

function gitItems(value: string | null | undefined) {
  const parsed = parseJsonValue(value)
  const list = Array.isArray(parsed) ? parsed : asObject(parsed) ? [parsed] : []
  return list.map((item, index) => {
    const object = asObject(item)
    return {
      key: `git-${index}`,
      commit: readableJsonValue(object?.commitSha ?? object?.sha ?? object?.commit ?? object?.id ?? `Git 线索 ${index + 1}`),
      message: readableJsonValue(object?.commitMessage ?? object?.message ?? object?.summary ?? item),
      author: readableJsonValue(object?.author ?? object?.authorName),
      branch: readableJsonValue(object?.branch ?? object?.branchName),
    }
  })
}

function jsonFieldItems(value: string | null | undefined) {
  if (!value || !value.trim()) return []
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) {
      return parsed.map((item, index) => ({
        key: `#${index + 1}`,
        value: typeof item === 'string' ? item : JSON.stringify(item, null, 2),
      }))
    }
    if (parsed && typeof parsed === 'object') {
      return Object.entries(parsed).map(([key, item]) => ({
        key,
        value: typeof item === 'string' ? item : JSON.stringify(item, null, 2),
      }))
    }
  } catch {
    return []
  }
  return []
}

function selectInsight(item: LearningInsight) {
  selectedInsightId.value = item.id
  verificationResult.value = null
  refineSuggestion.value = null
  refineInstruction.value = ''
  refineMessages.value = []
}

async function loadAll() {
  if (!projectSpaceId.value || Number.isNaN(projectSpaceId.value)) return
  loading.value = true
  try {
    if (!projectSpaceStore.spaces.length) {
      await projectSpaceStore.fetch()
    }
    const [nextSummary, nextRawEvents, nextInsights, nextKnowledge, nextAgentRuns] = await Promise.all([
      api.getSummary(projectSpaceId.value),
      api.listRawEvents(projectSpaceId.value),
      api.listInsights(projectSpaceId.value, insightStatusFilter.value),
      api.listKnowledgeItems(projectSpaceId.value, knowledgeStatusFilter.value),
      api.listAgentRuns(projectSpaceId.value),
    ])
    summary.value = nextSummary
    settings.value = nextSummary.settings
    rawEvents.value = nextRawEvents
    insights.value = nextInsights
    knowledgeItems.value = nextKnowledge
    agentRuns.value = nextAgentRuns
    if (!nextInsights.some((item) => item.id === selectedInsightId.value)) {
      selectedInsightId.value = nextInsights[0]?.id ?? null
    }
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

async function runAgent() {
  if (!settings.value?.enabled) {
    ElMessage.warning('请先开启自学习功能')
    return
  }
  runningAgent.value = true
  try {
    lastAgentRun.value = await api.runSelfLearningAgent(projectSpaceId.value)
    activeTab.value = 'insights'
    const [nextSummary, nextRawEvents, nextInsights, nextAgentRuns] = await Promise.all([
      api.getSummary(projectSpaceId.value),
      api.listRawEvents(projectSpaceId.value),
      api.listInsights(projectSpaceId.value, insightStatusFilter.value),
      api.listAgentRuns(projectSpaceId.value),
    ])
    summary.value = nextSummary
    settings.value = nextSummary.settings
    rawEvents.value = nextRawEvents
    insights.value = nextInsights
    agentRuns.value = nextAgentRuns
    selectedInsightId.value = nextInsights[0]?.id ?? null
    ElMessage.success(lastAgentRun.value.message)
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
    const [nextSummary, nextRawEvents] = await Promise.all([
      api.getSummary(projectSpaceId.value),
      api.listRawEvents(projectSpaceId.value),
    ])
    summary.value = nextSummary
    settings.value = nextSummary.settings
    rawEvents.value = nextRawEvents
    ElMessage.success(`${result.message} 已跳过 ${result.skippedRawEventCount} 条已有记录。`)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '导入历史聊天失败')
  } finally {
    importingHistory.value = false
  }
}

async function cleanupLegacyRawEvents() {
  await ElMessageBox.confirm(
    '将删除旧粒度的 USER_QUESTION / QUERY_PLAN / ASSISTANT_ANSWER 原始记录；关联的未审核洞察会删除，已生成正式知识的内容会标记为待复核。确认继续？',
    '清理旧粒度记录',
    { type: 'warning' }
  )
  cleaningLegacy.value = true
  try {
    const result = await api.cleanupLegacyRawEvents(projectSpaceId.value)
    activeTab.value = 'raw'
    const [nextSummary, nextRawEvents, nextInsights, nextKnowledge] = await Promise.all([
      api.getSummary(projectSpaceId.value),
      api.listRawEvents(projectSpaceId.value),
      api.listInsights(projectSpaceId.value, insightStatusFilter.value),
      api.listKnowledgeItems(projectSpaceId.value, knowledgeStatusFilter.value),
    ])
    summary.value = nextSummary
    settings.value = nextSummary.settings
    rawEvents.value = nextRawEvents
    insights.value = nextInsights
    knowledgeItems.value = nextKnowledge
    ElMessage.success(
      `${result.message} 删除洞察 ${result.deletedInsightCount} 条，标记待复核知识 ${result.staleKnowledgeItemCount} 条。`
    )
  } catch (err) {
    if (err !== 'cancel') {
      ElMessage.error(err instanceof Error ? err.message : '清理旧粒度记录失败')
    }
  } finally {
    cleaningLegacy.value = false
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
    await loadAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '清理旧版候选洞察失败')
  } finally {
    cleaningLegacyInsights.value = false
  }
}

function updateSettingFlag(key: keyof SelfLearningSettings, value: boolean | string | number) {
  updateSettings({ [key]: Boolean(value) })
}

function resetInsightForm() {
  editingInsightId.value = null
  Object.assign(insightForm, {
    type: 'QUESTION_ANSWER',
    status: 'PENDING_REVIEW',
    title: '',
    summary: '',
    conclusion: '',
    businessContext: '',
    glossaryMappingsJson: '',
    codeSymbolsJson: '',
    warnings: '',
    applicableScope: '',
    evidenceJson: '',
    gitProvenanceJson: '',
    tags: '',
    confidence: 0.5,
  })
}

function openInsightDialog(item?: LearningInsight) {
  resetInsightForm()
  if (item) {
    editingInsightId.value = item.id
    Object.assign(insightForm, {
      type: item.type,
      status: item.status,
      title: item.title,
      summary: item.summary ?? '',
      conclusion: item.conclusion,
      businessContext: item.businessContext ?? '',
      glossaryMappingsJson: item.glossaryMappingsJson ?? '',
      codeSymbolsJson: item.codeSymbolsJson ?? '',
      warnings: item.warnings ?? '',
      applicableScope: item.applicableScope ?? '',
      evidenceJson: item.evidenceJson ?? '',
      gitProvenanceJson: item.gitProvenanceJson ?? '',
      tags: item.tags ?? '',
      confidence: item.confidence,
    })
  }
  insightDialogVisible.value = true
}

async function saveInsight() {
  if (!insightForm.title.trim() || !insightForm.conclusion.trim()) {
    ElMessage.warning('请填写洞察标题和结论')
    return
  }
  saving.value = true
  try {
    if (editingInsightId.value) {
      await api.updateInsight(projectSpaceId.value, editingInsightId.value, insightForm)
    } else {
      await api.createInsight(projectSpaceId.value, insightForm)
    }
    insightDialogVisible.value = false
    insights.value = await api.listInsights(projectSpaceId.value, insightStatusFilter.value)
    selectedInsightId.value = editingInsightId.value ?? insights.value[0]?.id ?? null
    await reloadSummary()
    ElMessage.success('候选洞察已保存')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存候选洞察失败')
  } finally {
    saving.value = false
  }
}

async function approveInsight(item: LearningInsight) {
  await api.approveInsight(projectSpaceId.value, item.id, '管理员审核通过，归纳为正式知识。')
  insights.value = await api.listInsights(projectSpaceId.value, insightStatusFilter.value)
  knowledgeItems.value = await api.listKnowledgeItems(projectSpaceId.value, knowledgeStatusFilter.value)
  selectedInsightId.value = insights.value[0]?.id ?? null
  await reloadSummary()
  ElMessage.success('已审核通过并生成正式知识')
}

async function rejectInsight(item: LearningInsight) {
  await ElMessageBox.confirm('确认拒绝这条候选洞察？拒绝后不会进入正式知识库。', '拒绝洞察', {
    type: 'warning',
  })
  await api.rejectInsight(projectSpaceId.value, item.id, '管理员拒绝，未进入正式知识库。')
  insights.value = await api.listInsights(projectSpaceId.value, insightStatusFilter.value)
  selectedInsightId.value = insights.value[0]?.id ?? null
  await reloadSummary()
  ElMessage.success('候选洞察已拒绝')
}

async function verifySelectedInsight() {
  if (!selectedInsight.value) return
  verifyingInsight.value = true
  verificationResult.value = null
  try {
    verificationResult.value = await api.verifyInsight(projectSpaceId.value, selectedInsight.value.id)
    ElMessage.success('Insight Review Agent 已完成代码复核')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '洞察复核失败')
  } finally {
    verifyingInsight.value = false
  }
}

function openRefineDialog() {
  if (!selectedInsight.value) return
  refineDialogVisible.value = true
  refineSuggestion.value = null
  refineInstruction.value = ''
  refineMessages.value = []
}

async function refineSelectedInsight() {
  if (!selectedInsight.value || !refineInstruction.value.trim()) {
    ElMessage.warning('请先输入希望微调的内容')
    return
  }
  const userInstruction = refineInstruction.value.trim()
  refineMessages.value.push({ role: 'user', content: userInstruction })
  const conversationInstruction = refineMessages.value
    .map((message) => `${message.role === 'user' ? '管理员' : 'Insight Refine Agent'}：${message.content}`)
    .join('\n')
  refiningInsight.value = true
  try {
    const result = await api.refineInsight(projectSpaceId.value, selectedInsight.value.id, conversationInstruction)
    refineSuggestion.value = result.suggestion
    refineMessages.value.push({
      role: 'assistant',
      content: `${result.assistantMessage}\n建议标题：${result.suggestion.title}\n建议结论：${result.suggestion.conclusion}`,
    })
    refineInstruction.value = ''
    ElMessage.success(result.assistantMessage)
  } catch (err) {
    refineMessages.value.push({
      role: 'assistant',
      content: err instanceof Error ? `微调失败：${err.message}` : '微调失败',
    })
    ElMessage.error(err instanceof Error ? err.message : '洞察微调失败')
  } finally {
    refiningInsight.value = false
  }
}

function applyRefineSuggestion() {
  if (!selectedInsight.value || !refineSuggestion.value) return
  openInsightDialog(selectedInsight.value)
  Object.assign(insightForm, refineSuggestion.value)
  refineDialogVisible.value = false
}

function resetKnowledgeForm() {
  editingKnowledgeId.value = null
  Object.assign(knowledgeForm, {
    type: 'QUESTION_ANSWER',
    status: 'VERIFIED',
    title: '',
    content: '',
    summary: '',
    applicableScope: '',
    evidenceJson: '',
    gitProvenanceJson: '',
    tags: '',
    confidence: 0.8,
  })
}

function openKnowledgeDialog(item?: LearningKnowledgeItem) {
  resetKnowledgeForm()
  if (item) {
    editingKnowledgeId.value = item.id
    Object.assign(knowledgeForm, {
      type: item.type,
      status: item.status,
      title: item.title,
      content: item.content,
      summary: item.summary ?? '',
      applicableScope: item.applicableScope ?? '',
      evidenceJson: item.evidenceJson ?? '',
      gitProvenanceJson: item.gitProvenanceJson ?? '',
      tags: item.tags ?? '',
      confidence: item.confidence,
    })
  }
  knowledgeDialogVisible.value = true
}

async function saveKnowledge() {
  if (!knowledgeForm.title.trim() || !knowledgeForm.content.trim()) {
    ElMessage.warning('请填写知识标题和内容')
    return
  }
  saving.value = true
  try {
    if (editingKnowledgeId.value) {
      await api.updateKnowledgeItem(projectSpaceId.value, editingKnowledgeId.value, knowledgeForm)
    } else {
      await api.createKnowledgeItem(projectSpaceId.value, knowledgeForm)
    }
    knowledgeDialogVisible.value = false
    knowledgeItems.value = await api.listKnowledgeItems(projectSpaceId.value, knowledgeStatusFilter.value)
    await reloadSummary()
    ElMessage.success('正式知识已保存')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存正式知识失败')
  } finally {
    saving.value = false
  }
}

async function archiveKnowledge(item: LearningKnowledgeItem) {
  await api.archiveKnowledgeItem(projectSpaceId.value, item.id)
  knowledgeItems.value = await api.listKnowledgeItems(projectSpaceId.value, knowledgeStatusFilter.value)
  await reloadSummary()
  ElMessage.success('正式知识已归档')
}

async function markKnowledgeStale(item: LearningKnowledgeItem) {
  await api.markKnowledgeItemStale(projectSpaceId.value, item.id, '管理员标记为待复核。')
  knowledgeItems.value = await api.listKnowledgeItems(projectSpaceId.value, knowledgeStatusFilter.value)
  await reloadSummary()
  ElMessage.success('正式知识已标记为待复核')
}

async function deleteKnowledge(item: LearningKnowledgeItem) {
  await ElMessageBox.confirm('确认删除这条正式知识？删除后无法参与后续回答召回。', '删除知识', {
    type: 'warning',
  })
  await api.deleteKnowledgeItem(projectSpaceId.value, item.id)
  knowledgeItems.value = await api.listKnowledgeItems(projectSpaceId.value, knowledgeStatusFilter.value)
  await reloadSummary()
  ElMessage.success('正式知识已删除')
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
        <p>{{ activeKnowledge.length }} 条可作为回答线索</p>
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
              <span>{{ formatTime(run.updatedAt) }}</span>
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
          <div class="tab-toolbar">
            <div>
              <h3>Raw Events</h3>
              <p>只做事实留痕，不直接参与回答召回。历史聊天可手动导入，重复导入会自动跳过。</p>
            </div>
            <div class="toolbar-actions">
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
                <span>{{ formatTime(event.createdAt) }}</span>
              </div>
              <h4>{{ event.summary || '未提供摘要' }}</h4>
              <p>Agent：{{ event.agentId || 'system' }} · Question #{{ event.questionId || '-' }}</p>
            </article>
            <el-empty v-if="!rawEvents.length" description="还没有原始记录。开启自学习并完成问答后会自动沉淀。" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="待审核洞察" name="insights">
          <div class="tab-toolbar">
            <div>
              <h3>Learning Insights</h3>
              <p>候选洞察需要管理员审核，通过后才会归纳为正式知识。</p>
            </div>
            <div class="toolbar-actions">
              <el-select v-model="insightStatusFilter" clearable placeholder="状态筛选" @change="loadAll">
                <el-option v-for="item in insightStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button type="primary" @click="openInsightDialog()">
                <Plus class="button-icon" :size="16" :stroke-width="1.8" />
                新建洞察
              </el-button>
            </div>
          </div>
          <div v-if="insights.length" class="review-workbench">
            <aside class="review-queue">
              <div class="review-queue-head">
                <strong>审核队列</strong>
                <span>{{ pendingInsights.length }} 条待处理</span>
              </div>
              <button
                v-for="item in insights"
                :key="item.id"
                class="review-list-item"
                :class="{ active: selectedInsight?.id === item.id }"
                type="button"
                @click="selectInsight(item)"
              >
                <span class="review-list-title">{{ item.title }}</span>
                <span class="review-list-meta">
                  <el-tag :type="insightTagType(item.status)" size="small">{{ insightStatusLabel(item.status) }}</el-tag>
                  {{ typeLabel(item.type) }}
                </span>
              </button>
            </aside>

            <article v-if="selectedInsight" class="review-detail">
              <div class="review-detail-head">
                <div>
                  <div class="card-meta">
                    <el-tag :type="insightTagType(selectedInsight.status)" size="small">
                      {{ insightStatusLabel(selectedInsight.status) }}
                    </el-tag>
                    <span>{{ typeLabel(selectedInsight.type) }} · 置信度 {{ Math.round(selectedInsight.confidence * 100) }}%</span>
                  </div>
                  <h4>{{ selectedInsight.title }}</h4>
                  <p>{{ compactText(selectedInsight.summary, '暂无摘要') }}</p>
                </div>
                <div class="review-actions">
                  <el-button :loading="verifyingInsight" @click="verifySelectedInsight">
                    <Bot class="button-icon" :size="14" :stroke-width="1.8" />
                    Agent 复核
                  </el-button>
                  <el-button plain @click="openRefineDialog">AI 微调</el-button>
                  <el-button @click="openInsightDialog(selectedInsight)">编辑</el-button>
                  <el-button
                    v-if="selectedInsight.status === 'PENDING_REVIEW'"
                    type="success"
                    @click="approveInsight(selectedInsight)"
                  >
                    <CheckCircle2 class="button-icon" :size="14" :stroke-width="1.8" />
                    审核通过
                  </el-button>
                  <el-button
                    v-if="selectedInsight.status === 'PENDING_REVIEW'"
                    type="danger"
                    plain
                    @click="rejectInsight(selectedInsight)"
                  >
                    拒绝
                  </el-button>
                </div>
              </div>

              <div class="review-checklist">
                <span>审核前确认：</span>
                <strong>对用户有用</strong>
                <strong>与当前代码不冲突</strong>
                <strong>证据或 Git 追溯足够</strong>
              </div>

              <section class="review-agent-panel">
                <div class="review-agent-copy">
                  <div class="agent-panel-title">
                    <Bot :size="18" :stroke-width="2" />
                    <strong>Insight Review Agent</strong>
                    <el-tag
                      v-if="verificationResult"
                      size="small"
                      :type="verificationStatusType(verificationResult.status)"
                    >
                      {{ verificationStatusLabel(verificationResult.status) }}
                    </el-tag>
                  </div>
                  <p v-if="verificationResult">{{ compactText(verificationResult.summary, '复核完成，但没有返回摘要。') }}</p>
                  <p v-else>点击“Agent 复核”后，会基于这条洞察去当前代码中重新查询证据，并生成审核建议。</p>
                </div>
                <div v-if="verificationResult" class="verification-grid">
                  <div>
                    <span>复核置信度</span>
                    <strong>{{ Math.round((verificationResult.confidence ?? 0) * 100) }}%</strong>
                  </div>
                  <div>
                    <span>复核时间</span>
                    <strong>{{ formatTime(verificationResult.verifiedAt) }}</strong>
                  </div>
                </div>
                <div v-if="verificationResult?.suggestedChanges" class="agent-suggestion">
                  <strong>建议修改</strong>
                  <p>{{ verificationResult.suggestedChanges }}</p>
                </div>
                <div v-if="verificationResult?.suggestedWarnings" class="agent-suggestion warning">
                  <strong>建议保留的风险提醒</strong>
                  <p>{{ verificationResult.suggestedWarnings }}</p>
                </div>
                <div v-if="verificationResult?.codeEvidenceJson" class="verification-evidence">
                  <strong>复核代码证据</strong>
                  <div v-if="evidenceItems(verificationResult.codeEvidenceJson).length" class="evidence-card-list">
                    <article v-for="entry in evidenceItems(verificationResult.codeEvidenceJson)" :key="entry.key" class="evidence-card">
                      <strong>{{ entry.title }}</strong>
                      <p>{{ entry.summary }}</p>
                      <div v-if="entry.meta.length" class="evidence-meta">
                        <span v-for="meta in entry.meta" :key="meta">{{ meta }}</span>
                      </div>
                    </article>
                  </div>
                  <div v-else-if="jsonFieldItems(verificationResult.codeEvidenceJson).length" class="json-render-list">
                    <div v-for="entry in jsonFieldItems(verificationResult.codeEvidenceJson)" :key="entry.key" class="json-render-item">
                      <span>{{ entry.key }}</span>
                      <pre>{{ entry.value }}</pre>
                    </div>
                  </div>
                  <pre v-else>{{ prettyJson(verificationResult.codeEvidenceJson) }}</pre>
                </div>
                <div v-if="verificationResult?.gitProvenanceJson" class="verification-evidence">
                  <strong>复核 Git 线索</strong>
                  <div v-if="gitItems(verificationResult.gitProvenanceJson).length" class="git-card-list">
                    <article v-for="entry in gitItems(verificationResult.gitProvenanceJson)" :key="entry.key" class="git-card">
                      <code>{{ entry.commit }}</code>
                      <strong>{{ entry.message }}</strong>
                      <span>{{ entry.author }} · {{ entry.branch }}</span>
                    </article>
                  </div>
                  <pre v-else>{{ prettyJson(verificationResult.gitProvenanceJson) }}</pre>
                </div>
              </section>

              <section class="review-section primary">
                <h5>候选结论</h5>
                <p>{{ compactText(selectedInsight.conclusion) }}</p>
              </section>

              <div class="review-section-grid">
                <section class="review-section">
                  <h5>业务语境</h5>
                  <p>{{ compactText(selectedInsight.businessContext, '未提取业务语境，审核时建议补充。') }}</p>
                </section>
                <section class="review-section">
                  <h5>适用范围</h5>
                  <p>{{ compactText(selectedInsight.applicableScope, '未限定适用范围，审核时建议补充。') }}</p>
                </section>
              </div>

              <div class="review-section-grid">
                <section class="review-section code-block">
                  <div class="section-title-row">
                    <h5>代码名词映射</h5>
                    <el-tag v-if="glossaryItems(selectedInsight.glossaryMappingsJson).length" size="small" effect="plain">术语</el-tag>
                  </div>
                  <div v-if="glossaryItems(selectedInsight.glossaryMappingsJson).length" class="glossary-list">
                    <article v-for="entry in glossaryItems(selectedInsight.glossaryMappingsJson)" :key="entry.key" class="glossary-card">
                      <div class="glossary-main">
                        <strong>{{ entry.term }}</strong>
                        <span>对应</span>
                        <code>{{ entry.codeSymbol }}</code>
                      </div>
                      <p>{{ entry.meaning }}</p>
                      <small v-if="entry.source !== '未提供'">来源：{{ entry.source }}</small>
                    </article>
                  </div>
                  <pre v-else>{{ prettyJson(selectedInsight.glossaryMappingsJson) || '暂无代码名词映射' }}</pre>
                </section>
                <section class="review-section code-block">
                  <div class="section-title-row">
                    <h5>代码符号</h5>
                    <el-tag v-if="symbolItems(selectedInsight.codeSymbolsJson).length" size="small" effect="plain">Symbol</el-tag>
                  </div>
                  <div v-if="symbolItems(selectedInsight.codeSymbolsJson).length" class="symbol-list">
                    <div v-for="entry in symbolItems(selectedInsight.codeSymbolsJson)" :key="entry.key" class="symbol-pill">
                      <code>{{ entry.symbol }}</code>
                      <span v-if="entry.kind">{{ entry.kind }}</span>
                      <small v-if="entry.file">{{ entry.file }}</small>
                    </div>
                  </div>
                  <pre v-else>{{ prettyJson(selectedInsight.codeSymbolsJson) || '暂无代码符号' }}</pre>
                </section>
              </div>

              <section v-if="selectedInsight.warnings" class="review-section warning">
                <h5>注意事项</h5>
                <p>{{ selectedInsight.warnings }}</p>
              </section>

              <div class="review-section-grid">
                <section class="review-section code-block">
                  <div class="section-title-row">
                    <h5>证据</h5>
                    <el-tag v-if="evidenceItems(selectedInsight.evidenceJson).length" size="small" effect="plain">Evidence</el-tag>
                  </div>
                  <div v-if="evidenceItems(selectedInsight.evidenceJson).length" class="evidence-card-list">
                    <article v-for="entry in evidenceItems(selectedInsight.evidenceJson)" :key="entry.key" class="evidence-card">
                      <strong>{{ entry.title }}</strong>
                      <p>{{ entry.summary }}</p>
                      <div v-if="entry.meta.length" class="evidence-meta">
                        <span v-for="meta in entry.meta" :key="meta">{{ meta }}</span>
                      </div>
                    </article>
                  </div>
                  <pre v-else>{{ prettyJson(selectedInsight.evidenceJson) || '暂无证据' }}</pre>
                </section>
                <section class="review-section code-block">
                  <div class="section-title-row">
                    <h5>Git 追溯</h5>
                    <el-tag v-if="gitItems(selectedInsight.gitProvenanceJson).length" size="small" effect="plain">Git</el-tag>
                  </div>
                  <div v-if="gitItems(selectedInsight.gitProvenanceJson).length" class="git-card-list">
                    <article v-for="entry in gitItems(selectedInsight.gitProvenanceJson)" :key="entry.key" class="git-card">
                      <code>{{ entry.commit }}</code>
                      <strong>{{ entry.message }}</strong>
                      <span>{{ entry.author }} · {{ entry.branch }}</span>
                    </article>
                  </div>
                  <pre v-else>{{ prettyJson(selectedInsight.gitProvenanceJson) || '暂无 Git 追溯' }}</pre>
                </section>
              </div>

              <div class="structured-fields">
                <span v-if="selectedInsight.sourceRawEventIdsJson">原始记录 {{ selectedInsight.sourceRawEventIdsJson }}</span>
                <span v-if="selectedInsight.sourceQuestionIdsJson">问题 {{ selectedInsight.sourceQuestionIdsJson }}</span>
                <span v-if="selectedInsight.tags">标签 {{ selectedInsight.tags }}</span>
              </div>
            </article>
          </div>
          <el-empty v-else description="暂无候选洞察。" />
        </el-tab-pane>

        <el-tab-pane label="正式知识" name="knowledge">
          <div class="tab-toolbar">
            <div>
              <h3>Knowledge Items</h3>
              <p>只有 active / verified 正式知识会作为回答线索召回。</p>
            </div>
            <div class="toolbar-actions">
              <el-select v-model="knowledgeStatusFilter" clearable placeholder="状态筛选" @change="loadAll">
                <el-option v-for="item in knowledgeStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
              <el-button type="primary" @click="openKnowledgeDialog()">
                <Plus class="button-icon" :size="16" :stroke-width="1.8" />
                新建知识
              </el-button>
            </div>
          </div>
          <div class="insight-grid">
            <article v-for="item in knowledgeItems" :key="item.id" class="knowledge-card is-knowledge">
              <div class="card-meta">
                <el-tag :type="knowledgeTagType(item.status)" size="small">{{ knowledgeStatusLabel(item.status) }}</el-tag>
                <span>{{ typeLabel(item.type) }}</span>
              </div>
              <h4>{{ item.title }}</h4>
              <p>{{ item.summary || item.content }}</p>
              <div class="structured-fields">
                <span v-if="item.sourceInsightIdsJson">来源洞察</span>
                <span v-if="item.sourceRawEventIdsJson">原始记录</span>
                <span v-if="item.evidenceJson">证据</span>
                <span v-if="item.tags">标签</span>
              </div>
              <div class="evidence-row">
                <GitBranch :size="14" :stroke-width="1.8" />
                <span>{{ item.gitProvenanceJson ? '包含 Git 追溯' : '等待补充 Git 追溯' }}</span>
              </div>
              <div class="card-actions">
                <el-button size="small" @click="openKnowledgeDialog(item)">编辑</el-button>
                <el-button v-if="item.status !== 'STALE'" size="small" type="warning" plain @click="markKnowledgeStale(item)">
                  标记待复核
                </el-button>
                <el-button size="small" plain @click="archiveKnowledge(item)">归档</el-button>
                <el-button size="small" type="danger" plain @click="deleteKnowledge(item)">
                  <Trash2 class="button-icon" :size="14" :stroke-width="1.8" />
                  删除
                </el-button>
              </div>
            </article>
            <el-empty v-if="!knowledgeItems.length" description="暂无正式知识。" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>

    <el-dialog v-model="insightDialogVisible" :title="editingInsightId ? '编辑候选洞察' : '新建候选洞察'" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="类型">
            <el-select v-model="insightForm.type">
              <el-option v-for="item in knowledgeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="insightForm.status">
              <el-option v-for="item in insightStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标题">
          <el-input v-model="insightForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="insightForm.summary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="结论">
          <el-input v-model="insightForm.conclusion" type="textarea" :rows="4" />
        </el-form-item>
        <el-form-item label="业务语境">
          <el-input v-model="insightForm.businessContext" type="textarea" :rows="3" placeholder="记录业务逻辑、适用场景、用户表达方式" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="代码名词映射 JSON">
            <el-input v-model="insightForm.glossaryMappingsJson" type="textarea" :rows="3" placeholder='例如 [{"term":"Order","meaning":"订单聚合"}]' />
          </el-form-item>
          <el-form-item label="代码符号 JSON">
            <el-input v-model="insightForm.codeSymbolsJson" type="textarea" :rows="3" placeholder='例如 ["PaymentService.pay"]' />
          </el-form-item>
        </div>
        <el-form-item label="注意事项">
          <el-input v-model="insightForm.warnings" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="适用范围">
          <el-input v-model="insightForm.applicableScope" type="textarea" :rows="2" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="证据 JSON">
            <el-input v-model="insightForm.evidenceJson" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="Git 追溯 JSON">
            <el-input v-model="insightForm.gitProvenanceJson" type="textarea" :rows="3" />
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-input v-model="insightForm.tags" maxlength="500" placeholder="用逗号分隔，如 payment,domain-term" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="insightDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveInsight">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="refineDialogVisible" title="AI 微调候选洞察" width="780px">
      <div class="refine-dialog">
        <section class="refine-current" v-if="selectedInsight">
          <span>当前洞察</span>
          <strong>{{ selectedInsight.title }}</strong>
          <p>{{ compactText(selectedInsight.conclusion) }}</p>
        </section>
        <section class="refine-chat">
          <div class="section-title-row">
            <h5>微调对话</h5>
            <el-tag size="small" effect="plain">LLM Agent</el-tag>
          </div>
          <div v-if="refineMessages.length" class="refine-message-list">
            <div
              v-for="(message, index) in refineMessages"
              :key="`${message.role}-${index}`"
              class="refine-message"
              :class="message.role"
            >
              <span>{{ message.role === 'user' ? '管理员' : 'Insight Refine Agent' }}</span>
              <p>{{ message.content }}</p>
            </div>
          </div>
          <p v-else class="empty-chat-hint">输入你的调整要求，例如“结论更谨慎一点”或“补充业务术语和代码名词映射”。</p>
        </section>
        <el-form label-position="top">
          <el-form-item label="告诉 Insight Refine Agent 你希望怎么改">
            <el-input
              v-model="refineInstruction"
              type="textarea"
              :rows="4"
              placeholder="例如：把结论改得更谨慎，补充适用范围，并把代码名词映射写成业务人员能看懂的描述。"
            />
          </el-form-item>
        </el-form>
        <section v-if="refineSuggestion" class="refine-suggestion">
          <div class="section-title-row">
            <h5>Agent 建议稿</h5>
            <el-tag size="small" type="warning" effect="plain">待人工保存</el-tag>
          </div>
          <strong>{{ refineSuggestion.title }}</strong>
          <p>{{ refineSuggestion.conclusion }}</p>
          <div class="review-section-grid">
            <div>
              <span>业务语境</span>
              <p>{{ compactText(refineSuggestion.businessContext, '未建议修改') }}</p>
            </div>
            <div>
              <span>适用范围</span>
              <p>{{ compactText(refineSuggestion.applicableScope, '未建议修改') }}</p>
            </div>
          </div>
          <div v-if="refineSuggestion.warnings" class="agent-suggestion warning">
            <strong>注意事项</strong>
            <p>{{ refineSuggestion.warnings }}</p>
          </div>
        </section>
      </div>
      <template #footer>
        <el-button @click="refineDialogVisible = false">关闭</el-button>
        <el-button :loading="refiningInsight" @click="refineSelectedInsight">生成建议</el-button>
        <el-button type="primary" :disabled="!refineSuggestion" @click="applyRefineSuggestion">应用到编辑表单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="knowledgeDialogVisible" :title="editingKnowledgeId ? '编辑正式知识' : '新建正式知识'" width="720px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="类型">
            <el-select v-model="knowledgeForm.type">
              <el-option v-for="item in knowledgeTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="knowledgeForm.status">
              <el-option v-for="item in knowledgeStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="标题">
          <el-input v-model="knowledgeForm.title" maxlength="200" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="knowledgeForm.summary" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="knowledgeForm.content" type="textarea" :rows="5" />
        </el-form-item>
        <el-form-item label="适用范围">
          <el-input v-model="knowledgeForm.applicableScope" type="textarea" :rows="2" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="证据 JSON">
            <el-input v-model="knowledgeForm.evidenceJson" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="Git 追溯 JSON">
            <el-input v-model="knowledgeForm.gitProvenanceJson" type="textarea" :rows="3" />
          </el-form-item>
        </div>
        <el-form-item label="标签">
          <el-input v-model="knowledgeForm.tags" maxlength="500" placeholder="用逗号分隔，如 payment,glossary,verified" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="knowledgeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveKnowledge">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.learning-page {
  width: min(1180px, calc(100vw - 48px));
  margin: 0 auto;
  padding: var(--spacing-6) 0 var(--spacing-10);
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
.tab-toolbar p,
.knowledge-card p,
.event-card p {
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

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-4);
}

.tab-toolbar h3 {
  margin: 0;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.event-list,
.insight-grid {
  display: grid;
  gap: var(--spacing-3);
}

.insight-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.event-card,
.knowledge-card {
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface-muted);
}

.event-head,
.card-meta,
.evidence-row,
.card-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.event-head,
.card-meta {
  justify-content: space-between;
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.event-card h4,
.knowledge-card h4 {
  margin: var(--spacing-3) 0 0;
}

.review-workbench {
  display: grid;
  grid-template-columns: minmax(240px, 320px) minmax(0, 1fr);
  gap: var(--spacing-4);
  align-items: start;
}

.review-queue,
.review-detail {
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface-muted);
}

.review-queue {
  position: sticky;
  top: var(--spacing-4);
  max-height: 72vh;
  overflow: auto;
  padding: var(--spacing-3);
}

.review-queue-head,
.review-detail-head,
.review-actions,
.review-checklist {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
}

.review-queue-head {
  justify-content: space-between;
  padding: var(--spacing-2) var(--spacing-2) var(--spacing-3);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.review-queue-head strong {
  color: var(--text);
}

.review-list-item {
  width: 100%;
  margin: 0 0 var(--spacing-2);
  padding: var(--spacing-3);
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}

.review-list-item:hover,
.review-list-item.active {
  border-color: rgba(79, 110, 247, 0.22);
  background: rgba(79, 110, 247, 0.08);
}

.review-list-title,
.review-list-meta {
  display: block;
}

.review-list-title {
  font-weight: var(--font-weight-semibold);
  line-height: 1.45;
}

.review-list-meta {
  margin-top: var(--spacing-2);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.review-detail {
  padding: var(--spacing-5);
}

.review-detail-head {
  justify-content: space-between;
  align-items: flex-start;
  border-bottom: 1px solid var(--stroke);
  padding-bottom: var(--spacing-4);
}

.review-detail-head h4 {
  margin: var(--spacing-3) 0 0;
  font-size: 1.2rem;
}

.review-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}

.review-checklist {
  flex-wrap: wrap;
  margin-top: var(--spacing-4);
  padding: var(--spacing-3);
  border-radius: var(--radius-md);
  background: rgba(34, 197, 94, 0.08);
  color: var(--muted);
}

.review-checklist strong {
  color: #166534;
}

.review-agent-panel {
  display: grid;
  gap: var(--spacing-3);
  margin-top: var(--spacing-4);
  padding: var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.2);
  border-radius: var(--radius-md);
  background: linear-gradient(135deg, rgba(79, 110, 247, 0.08), transparent 54%), var(--surface);
}

.agent-panel-title,
.section-title-row {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.agent-panel-title {
  flex-wrap: wrap;
  color: var(--chat-accent);
}

.review-agent-copy p,
.agent-suggestion p,
.verification-evidence pre,
.refine-current p,
.refine-suggestion p {
  margin: var(--spacing-2) 0 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.verification-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-3);
}

.verification-grid div {
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.verification-grid span,
.refine-current span,
.refine-suggestion span {
  display: block;
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.verification-grid strong {
  display: block;
  margin-top: var(--spacing-1);
}

.agent-suggestion,
.verification-evidence,
.refine-current,
.refine-suggestion {
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.agent-suggestion.warning {
  border-color: rgba(245, 158, 11, 0.24);
  background: rgba(245, 158, 11, 0.08);
}

.review-section-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-3);
}

.review-section {
  margin-top: var(--spacing-4);
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.review-section.primary {
  border-color: rgba(79, 110, 247, 0.2);
  background: linear-gradient(135deg, rgba(79, 110, 247, 0.06), transparent 48%), var(--surface);
}

.review-section.warning {
  border-color: rgba(245, 158, 11, 0.24);
  background: rgba(245, 158, 11, 0.08);
}

.review-section h5 {
  margin: 0 0 var(--spacing-2);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.section-title-row {
  justify-content: space-between;
  margin-bottom: var(--spacing-2);
}

.section-title-row h5 {
  margin: 0;
}

.review-section p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.review-section pre,
.verification-evidence pre,
.json-render-item pre {
  max-height: 240px;
  margin: 0;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--text);
  font-family: var(--font-mono);
  font-size: var(--font-size-sm);
  line-height: 1.6;
}

.json-render-list {
  display: grid;
  gap: var(--spacing-2);
}

.json-render-item {
  display: grid;
  grid-template-columns: minmax(72px, 120px) minmax(0, 1fr);
  gap: var(--spacing-2);
  align-items: start;
  padding: var(--spacing-2);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-sm);
  background: var(--surface-muted);
}

.json-render-item > span {
  color: var(--muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.json-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.json-chip-list span {
  padding: 4px 10px;
  border: 1px solid rgba(79, 110, 247, 0.18);
  border-radius: var(--radius-full);
  background: rgba(79, 110, 247, 0.08);
  color: var(--chat-accent);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.glossary-list,
.symbol-list,
.evidence-card-list,
.git-card-list {
  display: grid;
  gap: var(--spacing-2);
}

.glossary-card,
.evidence-card,
.git-card {
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.glossary-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-2);
}

.glossary-main strong {
  color: var(--text);
}

.glossary-main span,
.glossary-card small,
.symbol-pill small,
.git-card span,
.evidence-meta span {
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.glossary-main code,
.symbol-pill code,
.git-card code {
  padding: 2px 7px;
  border-radius: var(--radius-sm);
  background: rgba(79, 110, 247, 0.1);
  color: var(--chat-accent);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
}

.glossary-card p,
.evidence-card p {
  margin: var(--spacing-2) 0 0;
  color: var(--text);
  line-height: 1.65;
}

.symbol-list {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.symbol-pill {
  display: grid;
  gap: 3px;
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid rgba(79, 110, 247, 0.18);
  border-radius: var(--radius-md);
  background: rgba(79, 110, 247, 0.06);
}

.symbol-pill span {
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.evidence-card strong,
.git-card strong {
  display: block;
  margin-top: var(--spacing-1);
}

.evidence-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  margin-top: var(--spacing-2);
}

.evidence-meta span {
  padding: 2px 7px;
  border-radius: var(--radius-full);
  background: var(--surface);
}

.git-card {
  border-color: rgba(34, 197, 94, 0.18);
  background: rgba(34, 197, 94, 0.06);
}

.knowledge-card.is-knowledge {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.06), transparent 42%), var(--surface-muted);
}

.warning-strip {
  margin-top: var(--spacing-3);
  padding: var(--spacing-3);
  border-radius: var(--radius-md);
  background: rgba(245, 158, 11, 0.1);
  color: #92400e;
}

.structured-fields {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  margin-top: var(--spacing-3);
}

.structured-fields span {
  padding: 3px 8px;
  border: 1px solid rgba(79, 110, 247, 0.18);
  border-radius: var(--radius-full);
  background: rgba(79, 110, 247, 0.08);
  color: var(--chat-accent);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.evidence-row {
  margin-top: var(--spacing-3);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.card-actions {
  justify-content: flex-end;
  margin-top: var(--spacing-4);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-4);
}

.refine-dialog {
  display: grid;
  gap: var(--spacing-4);
}

.refine-chat {
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface);
}

.refine-message-list {
  display: grid;
  gap: var(--spacing-2);
}

.refine-message {
  max-width: 86%;
  padding: var(--spacing-3);
  border-radius: var(--radius-md);
  background: var(--surface-muted);
}

.refine-message.user {
  justify-self: end;
  border: 1px solid rgba(79, 110, 247, 0.18);
  background: rgba(79, 110, 247, 0.08);
}

.refine-message.assistant {
  justify-self: start;
  border: 1px solid rgba(34, 197, 94, 0.18);
  background: rgba(34, 197, 94, 0.08);
}

.refine-message span {
  display: block;
  color: var(--muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.refine-message p,
.empty-chat-hint {
  margin: var(--spacing-1) 0 0;
  white-space: pre-wrap;
  line-height: 1.65;
}

.empty-chat-hint {
  color: var(--muted);
}

.refine-suggestion {
  display: grid;
  gap: var(--spacing-3);
}

.button-icon {
  margin-right: var(--spacing-1);
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
  .insight-grid,
  .review-workbench,
  .review-section-grid,
  .verification-grid,
  .policy-switches {
    grid-template-columns: 1fr;
  }

  .json-render-item {
    grid-template-columns: 1fr;
  }

  .review-queue {
    position: static;
    max-height: none;
  }

  .tab-toolbar {
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
