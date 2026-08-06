<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bot, CheckCircle2, Plus } from 'lucide-vue-next'
import * as api from '../../services/selfLearningApi'
import { formatTime } from '../../utils/format'
import {
  compactText,
  evidenceItems,
  gitItems,
  glossaryItems,
  insightStatusLabel,
  insightTagType,
  jsonFieldItems,
  prettyJson,
  symbolItems,
  typeLabel,
  verificationStatusLabel,
  verificationStatusType,
} from '../../utils/selfLearningRender'
import type {
  LearningInsight,
  LearningInsightStatus,
  LearningInsightVerification,
  LearningKnowledgeType,
  SaveLearningInsightPayload,
} from '../../types/selfLearning'

type RefineChatMessage = {
  role: 'user' | 'assistant'
  content: string
}

const props = defineProps<{
  projectSpaceId: number
  refreshToken: number
}>()

const emit = defineEmits<{
  changed: []
}>()

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

const insights = ref<LearningInsight[]>([])
const loading = ref(false)
const saving = ref(false)
const insightStatusFilter = ref<LearningInsightStatus | ''>('')
const selectedInsightId = ref<number | null>(null)
const verificationResult = ref<LearningInsightVerification | null>(null)
const verifyingInsight = ref(false)
const insightDialogVisible = ref(false)
const editingInsightId = ref<number | null>(null)
const refineDialogVisible = ref(false)
const refiningInsight = ref(false)
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

const pendingInsights = computed(() => insights.value.filter((item) => item.status === 'PENDING_REVIEW'))
const selectedInsight = computed(() => {
  return insights.value.find((item) => item.id === selectedInsightId.value) ?? insights.value[0] ?? null
})

async function loadInsights() {
  loading.value = true
  try {
    insights.value = await api.listInsights(props.projectSpaceId, insightStatusFilter.value)
    if (!insights.value.some((item) => item.id === selectedInsightId.value)) {
      selectedInsightId.value = insights.value[0]?.id ?? null
      // 选中项被重置时同步清理复核/微调状态，避免展示过期结果
      resetReviewState()
    }
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载候选洞察失败')
  } finally {
    loading.value = false
  }
}

function selectInsight(item: LearningInsight) {
  selectedInsightId.value = item.id
  resetReviewState()
}

/** 清空复核/微调相关状态，避免切换洞察或筛选后展示过期复核结果。 */
function resetReviewState() {
  verificationResult.value = null
  refineSuggestion.value = null
  refineInstruction.value = ''
  refineMessages.value = []
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
      await api.updateInsight(props.projectSpaceId, editingInsightId.value, insightForm)
    } else {
      await api.createInsight(props.projectSpaceId, insightForm)
    }
    insightDialogVisible.value = false
    await loadInsights()
    selectedInsightId.value = editingInsightId.value ?? insights.value[0]?.id ?? null
    ElMessage.success('候选洞察已保存')
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存候选洞察失败')
  } finally {
    saving.value = false
  }
}

async function approveInsight(item: LearningInsight) {
  try {
    await api.approveInsight(props.projectSpaceId, item.id, '管理员审核通过，归纳为正式知识。')
    await loadInsights()
    selectedInsightId.value = insights.value[0]?.id ?? null
    ElMessage.success('已审核通过并生成正式知识')
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '审核通过失败')
  }
}

async function rejectInsight(item: LearningInsight) {
  try {
    await ElMessageBox.confirm('确认拒绝这条候选洞察？拒绝后不会进入正式知识库。', '拒绝洞察', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await api.rejectInsight(props.projectSpaceId, item.id, '管理员拒绝，未进入正式知识库。')
    await loadInsights()
    selectedInsightId.value = insights.value[0]?.id ?? null
    ElMessage.success('候选洞察已拒绝')
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '拒绝洞察失败')
  }
}

async function verifySelectedInsight() {
  if (!selectedInsight.value) return
  verifyingInsight.value = true
  verificationResult.value = null
  try {
    verificationResult.value = await api.verifyInsight(props.projectSpaceId, selectedInsight.value.id)
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
    const result = await api.refineInsight(props.projectSpaceId, selectedInsight.value.id, conversationInstruction)
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

onMounted(loadInsights)
watch(() => props.refreshToken, loadInsights)
</script>

<template>
  <div v-loading="loading" class="insights-section">
    <div class="section-heading">
      <div>
        <p class="kicker">待审核洞察</p>
        <h2>Learning Insights</h2>
        <p>候选洞察需要管理员审核，通过后才会归纳为正式知识。</p>
      </div>
      <div class="section-actions">
        <el-select v-model="insightStatusFilter" clearable placeholder="状态筛选" @change="loadInsights">
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
            {{ typeLabel(item.type, knowledgeTypeOptions) }}
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
              <span>{{ typeLabel(selectedInsight.type, knowledgeTypeOptions) }} · 置信度 {{ Math.round(selectedInsight.confidence * 100) }}%</span>
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
              <strong>{{ formatTime(verificationResult.verifiedAt, '未记录') }}</strong>
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
    <div v-else class="empty-box compact-empty">
      <p>暂无候选洞察。点击页面顶部「后台整理」从原始记录生成，或在右上角手动新建。</p>
    </div>

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
  </div>
</template>

<style scoped>
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

@media (max-width: 980px) {
  .insight-grid,
  .review-workbench,
  .review-section-grid,
  .verification-grid {
    grid-template-columns: 1fr;
  }

  .json-render-item {
    grid-template-columns: 1fr;
  }

  .review-queue {
    position: static;
    max-height: none;
  }
}
</style>
