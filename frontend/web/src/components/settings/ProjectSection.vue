<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { Plus, RefreshCw, RotateCcw, Save, Trash2, Undo2 } from 'lucide-vue-next'
import { useProjectStore } from '../../stores/project'
import { useRepositoryStore } from '../../stores/repository'
import { useDraftAutoSave } from '../../composables/useDraftAutoSave'
import { useChangeHistory } from '../../composables/useChangeHistory'

const projectStore = useProjectStore()
const repositoryStore = useRepositoryStore()
const showCreateProject = ref(false)
const projectQuery = ref('')
const memberQuery = ref('')
const selectedMemberIds = ref<number[]>([])

const formRef = ref(projectStore.form)
const { hasDraft, restore: restoreDraft, clear: clearDraft } = useDraftAutoSave(
  'project-form',
  formRef,
  3000
)
const { takeSnapshot, clearHistory } = useChangeHistory(formRef)

const selectedProject = computed(() =>
  projectStore.projects.find((project) => project.id === projectStore.selectedProjectId) ?? null
)

const filteredProjects = computed(() => {
  const q = projectQuery.value.trim().toLowerCase()
  if (!q) return projectStore.projects
  return projectStore.projects.filter(
    (project) =>
      project.name.toLowerCase().includes(q) ||
      (project.description && project.description.toLowerCase().includes(q))
  )
})

const filteredMembers = computed(() => {
  const q = memberQuery.value.trim().toLowerCase()
  if (!q) return projectStore.members
  return projectStore.members.filter(
    (member) =>
      (member.repository ?? '').toLowerCase().includes(q) ||
      member.alias.toLowerCase().includes(q) ||
      (member.defaultBranch ?? '').toLowerCase().includes(q)
  )
})

const nameError = computed(() => {
  if (!projectStore.form.name && showCreateProject.value) return '项目名称不能为空'
  if (projectStore.form.name.length > 120) return '项目名称不能超过 120 个字符'
  return ''
})

const aliasError = computed(() => {
  if (projectStore.memberForm.alias && !/^[a-zA-Z0-9_-]+$/.test(projectStore.memberForm.alias)) {
    return '目录别名只支持字母、数字、下划线和中划线'
  }
  return ''
})

const isFormValid = computed(() => !!projectStore.form.name.trim() && !nameError.value)

async function handleCreateProject() {
  if (!isFormValid.value) return
  takeSnapshot()
  const project = await projectStore.create()
  if (project) {
    ElMessage.success('项目已创建')
    showCreateProject.value = false
    clearDraft()
  }
}

async function selectProject(projectId: number | null) {
  if (projectId) {
    await projectStore.fetchMembers(projectId)
  } else {
    projectStore.members = []
  }
}

async function addRepository() {
  if (!selectedProject.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  if (!projectStore.memberForm.repositoryId) {
    ElMessage.warning('请选择仓库')
    return
  }
  takeSnapshot()
  const member = await projectStore.addRepository()
  if (member) {
    ElMessage.success('仓库已加入项目')
  }
}

async function removeRepository(memberId: number) {
  try {
    await ElMessageBox.confirm('确认将此仓库移出项目？', '移出确认', {
      confirmButtonText: '确认移出',
      cancelButtonText: '取消',
      type: 'warning',
    })
    takeSnapshot()
    const removed = await projectStore.removeRepository(memberId)
    if (removed) ElMessage.success('仓库已移出项目')
  } catch {
    // 用户取消
  }
}

async function batchRemove() {
  if (!selectedMemberIds.value.length) {
    ElMessage.warning('请先选择要移出的仓库')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量移出 ${selectedMemberIds.value.length} 个仓库？`,
      '批量移出确认',
      { confirmButtonText: '确认移出', cancelButtonText: '取消', type: 'warning' }
    )
    takeSnapshot()
    for (const id of selectedMemberIds.value) {
      await projectStore.removeRepository(id)
    }
    selectedMemberIds.value = []
    ElMessage.success('批量移出完成')
  } catch {
    // 用户取消
  }
}

async function restoreFromDraft() {
  const data = restoreDraft()
  if (data) {
    Object.assign(projectStore.form, data)
    ElMessage.success('已恢复草稿')
  }
}

function handleReset() {
  ElMessageBox.confirm('确认重置所有未保存的修改？', '重置确认', {
    confirmButtonText: '确认重置',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    projectStore.form.name = ''
    projectStore.form.description = ''
    projectStore.memberForm.repositoryId = null
    projectStore.memberForm.alias = ''
    projectStore.memberForm.role = 'repository'
    projectStore.memberForm.primaryRepository = false
    projectStore.memberForm.sortOrder = 0
    clearDraft()
    clearHistory()
    ElMessage.success('已重置')
  }).catch(() => {})
}
</script>

<template>
  <!-- 基础信息：项目选择 -->
  <section class="surface-panel settings-block config-section">
    <div class="section-heading">
      <div>
        <p class="kicker">1. 选择项目</p>
        <h2>选择一个业务项目作为分析范围</h2>
      </div>
      <div class="section-actions">
        <el-button
          circle
          :title="showCreateProject ? '收起新建项目' : '新建项目'"
          :aria-label="showCreateProject ? '收起新建项目' : '新建项目'"
          @click="showCreateProject = !showCreateProject"
        >
          <Plus aria-hidden="true" :size="16" :stroke-width="1.8" />
        </el-button>
        <el-button
          circle
          :loading="projectStore.loading"
          title="刷新项目"
          aria-label="刷新项目"
          @click="projectStore.fetch"
        >
          <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
        </el-button>
      </div>
    </div>

    <div class="config-search-bar">
      <el-input
        v-model="projectQuery"
        placeholder="搜索项目名称或描述..."
        :prefix-icon="Search"
        clearable
        size="default"
      />
    </div>

    <div class="project-picker-panel">
      <div class="subsection-heading">
        <strong>已有项目</strong>
        <span>{{ projectStore.projects.length }} 个</span>
      </div>
      <div v-if="filteredProjects.length" v-loading="projectStore.loading" class="project-card-list">
        <button
          v-for="project in filteredProjects"
          :key="project.id"
          class="project-card-button"
          type="button"
          :class="{ active: project.id === projectStore.selectedProjectId }"
          @click="selectProject(project.id)"
        >
          <span>{{ project.name }}</span>
          <small>{{ project.description || '未填写描述' }}</small>
          <em v-if="project.id === projectStore.selectedProjectId">当前</em>
        </button>
      </div>
      <div v-else v-loading="projectStore.loading" class="empty-box compact-empty">
        <p>{{ projectQuery ? '没有匹配的项目' : '还没有项目。点击右上角"新建项目"开始。' }}</p>
      </div>
    </div>

    <!-- 新建项目面板 -->
    <el-collapse-transition>
      <div v-if="showCreateProject" class="project-create-panel">
        <div class="subsection-heading">
          <strong>新建项目</strong>
          <span>用于归类仓库</span>
        </div>
        <div class="config-form-grid">
          <div class="config-field">
            <label class="field-label">
              项目名称 <span class="required-star">*</span>
            </label>
            <el-input
              v-model="projectStore.form.name"
              placeholder="例如 qys-skill"
              maxlength="120"
              clearable
              show-word-limit
              :class="{ 'is-error': nameError }"
            />
            <p v-if="nameError" class="field-error">{{ nameError }}</p>
          </div>
          <div class="config-field">
            <label class="field-label">描述</label>
            <el-input
              v-model="projectStore.form.description"
              placeholder="可选，用于说明项目边界"
              clearable
            />
          </div>
        </div>
        <div class="settings-actions">
          <el-button title="重置表单" aria-label="重置表单" @click="handleReset">
            <RotateCcw class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
            重置
          </el-button>
          <el-button
            type="primary"
            :loading="projectStore.loading"
            :disabled="!isFormValid"
            @click="handleCreateProject"
          >
            <Save class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
            创建项目
          </el-button>
          <el-button
            v-if="hasDraft"
            type="info"
            text
            title="恢复草稿"
            aria-label="恢复草稿"
            @click="restoreFromDraft"
          >
            <Undo2 class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
            恢复草稿
          </el-button>
        </div>
      </div>
    </el-collapse-transition>

    <el-alert v-if="projectStore.error" type="error" :title="projectStore.error" show-icon :closable="false" />
  </section>

  <!-- 仓库配置：权限与成员 -->
  <section v-if="selectedProject" class="surface-panel settings-block config-section">
    <div class="section-heading">
      <div>
        <p class="kicker">2. 确认仓库</p>
        <h2>{{ selectedProject.name }} 的仓库</h2>
      </div>
      <div class="section-actions">
        <span class="maintenance-summary-meta">{{ projectStore.members.length }} 个仓库</span>
        <el-button
          v-if="selectedMemberIds.length"
          type="danger"
          plain
          size="small"
          title="批量移出已选择仓库"
          aria-label="批量移出已选择仓库"
          @click="batchRemove"
        >
          <Trash2 class="button-icon" aria-hidden="true" :size="15" :stroke-width="1.8" />
          批量移出 ({{ selectedMemberIds.length }})
        </el-button>
      </div>
    </div>

    <div class="config-search-bar">
      <el-input
        v-model="memberQuery"
        placeholder="搜索仓库名称、别名..."
        :prefix-icon="Search"
        clearable
        size="default"
      />
    </div>

    <div class="settings-form-grid settings-form-grid-repo">
      <div class="config-field">
        <label class="field-label">
          仓库 <span class="required-star">*</span>
        </label>
        <el-select
          v-model="projectStore.memberForm.repositoryId"
          placeholder="选择仓库"
          clearable
          style="width: 100%"
        >
          <el-option
            v-for="repo in repositoryStore.repositories"
            :key="repo.id"
            :label="repo.name"
            :value="repo.id"
          />
        </el-select>
      </div>
      <div class="config-field">
        <label class="field-label">目录别名</label>
        <el-input
          v-model="projectStore.memberForm.alias"
          placeholder="例如 order-service"
          maxlength="120"
          clearable
          :class="{ 'is-error': aliasError }"
        />
        <p v-if="aliasError" class="field-error">{{ aliasError }}</p>
      </div>
      <div class="config-field">
        <label class="field-label">角色</label>
        <el-input
          v-model="projectStore.memberForm.role"
          placeholder="backend-service / frontend"
          maxlength="64"
          clearable
        />
      </div>
      <div class="config-field">
        <label class="field-label">排序</label>
        <el-input-number v-model="projectStore.memberForm.sortOrder" :min="0" style="width: 100%" />
      </div>
      <div class="config-field inline-field">
        <el-checkbox v-model="projectStore.memberForm.primaryRepository">核心仓库</el-checkbox>
      </div>
    </div>

    <div class="settings-actions">
      <el-button type="primary" @click="addRepository">
        <Plus class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        加入项目
      </el-button>
    </div>

    <el-table
      :data="filteredMembers"
      empty-text="当前项目还没有仓库"
      max-height="360"
      @selection-change="(rows: ProjectRepositoryMember[]) => selectedMemberIds = rows.map(r => r.id)"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column label="仓库" min-width="140">
        <template #default="{ row }">
          {{ row.repositoryName ?? row.repository }}
        </template>
      </el-table-column>
      <el-table-column prop="alias" label="目录别名" min-width="140" />
      <el-table-column label="默认分支" min-width="120">
        <template #default="{ row }">
          {{ row.defaultBranch ?? '未记录' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            circle
            type="danger"
            title="移出仓库"
            aria-label="移出仓库"
            @click="removeRepository(row.id)"
          >
            <Trash2 aria-hidden="true" :size="15" :stroke-width="1.8" />
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<script lang="ts">
import type { ProjectRepositoryMember } from '../../types/project'
</script>

<style scoped>
.section-actions {
  display: flex;
  gap: var(--spacing-2);
  align-items: center;
  flex-wrap: wrap;
}

.config-search-bar {
  margin-bottom: var(--spacing-4);
}

.config-section {
  border-radius: var(--radius-xl);
  transition:
    border-color var(--transition-normal),
    box-shadow var(--transition-normal);
}

.config-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--spacing-3);
}

.config-field {
  display: grid;
  gap: var(--spacing-1);
}

.config-field.inline-field {
  align-content: end;
  padding-bottom: 2px;
}

.required-star {
  color: var(--danger, #dc2626);
  font-weight: 700;
}

.field-error {
  margin: 2px 0 0;
  color: var(--danger, #dc2626);
  font-size: 12px;
  line-height: 1.4;
}

.project-picker-panel,
.project-create-panel {
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  padding: var(--spacing-4);
  background: var(--surface);
  box-shadow: var(--shadow-soft);
  margin-bottom: var(--spacing-4);
}

.subsection-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-3);
}

.subsection-heading span,
.maintenance-summary-meta {
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.project-card-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: var(--spacing-3);
}

.project-card-button {
  position: relative;
  display: grid;
  gap: var(--spacing-2);
  min-height: 82px;
  padding: var(--spacing-3);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface);
  color: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--transition-normal), box-shadow var(--transition-normal);
}

.project-card-button:hover {
  border-color: var(--chat-accent);
}

.project-card-button.active {
  border-color: rgba(79, 110, 247, 0.36);
  background: var(--chat-accent-soft);
  box-shadow: none;
}

.project-card-button span {
  font-weight: 700;
}

.project-card-button small {
  color: var(--muted);
  line-height: 1.4;
}

.project-card-button em {
  position: absolute;
  right: 10px;
  bottom: 8px;
  color: var(--chat-accent);
  font-size: var(--font-size-xs);
  font-style: normal;
}

@media (max-width: 900px) {
  .section-actions {
    width: 100%;
    justify-content: flex-start;
  }
  .config-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
