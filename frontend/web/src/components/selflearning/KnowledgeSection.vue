<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { GitBranch, Plus, Trash2 } from 'lucide-vue-next'
import * as api from '../../services/selfLearningApi'
import {
  knowledgeStatusLabel,
  knowledgeTagType,
  typeLabel,
} from '../../utils/selfLearningRender'
import type {
  LearningKnowledgeItem,
  LearningKnowledgeStatus,
  LearningKnowledgeType,
  SaveLearningKnowledgeItemPayload,
} from '../../types/selfLearning'

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

const knowledgeStatusOptions: Array<{ label: string; value: LearningKnowledgeStatus }> = [
  { label: '启用', value: 'ACTIVE' },
  { label: '已验证', value: 'VERIFIED' },
  { label: '待复核', value: 'STALE' },
  { label: '已过期', value: 'DEPRECATED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '防错案例', value: 'NEGATIVE' },
]

const knowledgeItems = ref<LearningKnowledgeItem[]>([])
const loading = ref(false)
const saving = ref(false)
const knowledgeStatusFilter = ref<LearningKnowledgeStatus | ''>('')
const knowledgeDialogVisible = ref(false)
const editingKnowledgeId = ref<number | null>(null)

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

async function loadKnowledgeItems() {
  loading.value = true
  try {
    knowledgeItems.value = await api.listKnowledgeItems(props.projectSpaceId, knowledgeStatusFilter.value)
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '加载正式知识失败')
  } finally {
    loading.value = false
  }
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
      await api.updateKnowledgeItem(props.projectSpaceId, editingKnowledgeId.value, knowledgeForm)
    } else {
      await api.createKnowledgeItem(props.projectSpaceId, knowledgeForm)
    }
    knowledgeDialogVisible.value = false
    resetKnowledgeForm()
    await loadKnowledgeItems()
    ElMessage.success('正式知识已保存')
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '保存正式知识失败')
  } finally {
    saving.value = false
  }
}

async function archiveKnowledge(item: LearningKnowledgeItem) {
  try {
    await api.archiveKnowledgeItem(props.projectSpaceId, item.id)
    await loadKnowledgeItems()
    ElMessage.success('正式知识已归档')
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '归档失败')
  }
}

async function markKnowledgeStale(item: LearningKnowledgeItem) {
  try {
    await api.markKnowledgeItemStale(props.projectSpaceId, item.id, '管理员标记为待复核。')
    await loadKnowledgeItems()
    ElMessage.success('正式知识已标记为待复核')
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '标记待复核失败')
  }
}

async function deleteKnowledge(item: LearningKnowledgeItem) {
  try {
    await ElMessageBox.confirm('确认删除这条正式知识？删除后无法参与后续回答召回。', '删除知识', {
      type: 'warning',
    })
  } catch {
    return
  }
  try {
    await api.deleteKnowledgeItem(props.projectSpaceId, item.id)
    await loadKnowledgeItems()
    ElMessage.success('正式知识已删除')
    emit('changed')
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '删除失败')
  }
}

onMounted(loadKnowledgeItems)
watch(() => props.refreshToken, loadKnowledgeItems)
</script>

<template>
  <div v-loading="loading" class="knowledge-section">
    <div class="section-heading">
      <div>
        <p class="kicker">Knowledge Items</p>
        <h2>正式知识</h2>
        <p>只有 active / verified 正式知识会作为回答线索召回。</p>
      </div>
      <div class="section-actions">
        <el-select v-model="knowledgeStatusFilter" clearable placeholder="状态筛选" @change="loadKnowledgeItems">
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
          <span>{{ typeLabel(item.type, knowledgeTypeOptions) }}</span>
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
      <div v-if="!knowledgeItems.length" class="empty-box compact-empty">
        <p>暂无正式知识。</p>
      </div>
    </div>

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
  </div>
</template>

<style scoped>
.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-3);
}

.knowledge-card {
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface-muted);
}

.knowledge-card h4 {
  margin: var(--spacing-3) 0 0;
}

.knowledge-card p {
  margin-top: var(--spacing-2);
  line-height: 1.65;
}

.knowledge-card.is-knowledge {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.06), transparent 42%), var(--surface-muted);
}

.card-meta,
.evidence-row,
.card-actions {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.card-meta {
  justify-content: space-between;
  color: var(--muted);
  font-size: var(--font-size-sm);
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

@media (max-width: 980px) {
  .insight-grid {
    grid-template-columns: 1fr;
  }
}
</style>
