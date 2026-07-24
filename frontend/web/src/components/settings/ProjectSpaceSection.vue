<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import {
  DatabaseZap,
  Eye,
  MessageSquare,
  RefreshCw,
  RotateCcw,
  Trash2,
  Undo2,
} from 'lucide-vue-next'
import { useProjectStore } from '../../stores/project'
import { useProjectSpaceStore } from '../../stores/projectSpace'
import { useRepositoryStore } from '../../stores/repository'
import { useDraftAutoSave } from '../../composables/useDraftAutoSave'
import { useChangeHistory } from '../../composables/useChangeHistory'
import type { RepositoryBranch } from '../../types/repository'

const props = withDefaults(defineProps<{
  mode?: 'create' | 'derive'
  projectId?: number | null
  sourceSpaceId?: number | null
}>(), {
  mode: 'create',
  projectId: null,
  sourceSpaceId: null,
})

const router = useRouter()
const projectStore = useProjectStore()
const projectSpaceStore = useProjectSpaceStore()
const repositoryStore = useRepositoryStore()
const creatingAndIndexing = ref(false)
const prefilledSourceId = ref<number | null>(null)
const showExistingSpaces = ref(!!props.projectId)
const spaceSearchQuery = ref('')
const nameTouched = ref(false)

const formRef = ref(projectSpaceStore.form)
const { hasDraft, restore: restoreDraft, clear: clearDraft } = useDraftAutoSave(
  'project-space-form',
  formRef,
  3000
)
const { takeSnapshot, clearHistory } = useChangeHistory(formRef)

const sourceSpace = computed(() =>
  props.sourceSpaceId == null
    ? null
    : projectSpaceStore.spaces.find((space) => space.id === props.sourceSpaceId) ?? null
)

const isDeriveMode = computed(() => props.mode === 'derive' && props.sourceSpaceId != null)
const selectedProject = computed(() =>
  projectStore.projects.find((project) => project.id === projectSpaceStore.form.projectId) ?? null
)
const suggestedSpaceName = computed(() => {
  if (!selectedProject.value) return ''
  const firstBranch = projectSpaceStore.form.memberBranches[0]?.branchName || projectSpaceStore.form.defaultBranch || 'master'
  return `${selectedProject.value.name}-${firstBranch}`
})
const selectedProjectMemberText = computed(() => {
  if (!selectedProject.value) return '选择项目后，系统会自动列出这个项目包含的仓库。'
  const count = projectSpaceStore.form.memberBranches.length
  return `${selectedProject.value.name} · ${count} 个仓库`
})

const spaceNameError = computed(() => {
  if (!projectSpaceStore.form.name.trim()) return '空间名称不能为空'
  if (projectSpaceStore.form.name.length > 120) return '空间名称不能超过 120 个字符'
  return ''
})

const isFormValid = computed(() =>
  !!projectSpaceStore.form.projectId &&
  !!projectSpaceStore.form.name.trim() &&
  projectSpaceStore.form.memberBranches.length > 0 &&
  projectSpaceStore.form.memberBranches.every((member) => member.branchId || member.branchName.trim()) &&
  !spaceNameError.value
)

const filteredSpaces = computed(() => {
  const q = spaceSearchQuery.value.trim().toLowerCase()
  let list = projectSpaceStore.spaces
  if (props.projectId) {
    list = list.filter((space) => space.projectId === props.projectId)
  }
  if (!q) return list
  return list.filter(
    (space) =>
      space.name.toLowerCase().includes(q) ||
      (space.project ?? '').toLowerCase().includes(q) ||
      projectSpaceStore.statusLabel(space.status).includes(q)
  )
})

function formatTime(iso?: string | null): string {
  if (!iso) return '未索引'
  try {
    const d = new Date(iso)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return iso
  }
}

function suggestedCopyName(name: string) {
  const base = `${name}-copy`
  return projectSpaceStore.spaces.some((space) => space.name === base) ? `${base}-${Date.now()}` : base
}

async function selectProject(projectId: number | null) {
  projectSpaceStore.form.projectId = projectId
  projectSpaceStore.form.memberBranches = []
  if (projectId) {
    await projectStore.fetchMembers(projectId)
    projectSpaceStore.generateMembers(projectStore.members)
    await hydrateBranchOptions()
  }
}

function branchOptions(repositoryId: number) {
  return (repositoryStore.branchesByRepository[repositoryId] ?? []).filter((branch) => branch.active)
}

function branchOptionLabel(branch: RepositoryBranch) {
  return `${branch.name} · ${repositoryStore.shortSha(branch.commitSha)} · ${repositoryStore.sourceKindLabel(branch.sourceKind)}`
}

async function hydrateBranchOptions() {
  const repositoryIds = [...new Set(projectSpaceStore.form.memberBranches.map((member) => member.repositoryId))]
  await Promise.all(repositoryIds.map(async (repositoryId) => {
    if (!repositoryStore.branchesByRepository[repositoryId]) {
      await repositoryStore.fetchBranches(repositoryId)
    }
  }))
  projectSpaceStore.form.memberBranches.forEach((member) => {
    if (member.branchId) return
    const options = branchOptions(member.repositoryId)
    const matched = options.find((branch) => branch.name === member.branchName)
      ?? options.find((branch) => branch.name === projectSpaceStore.form.defaultBranch)
      ?? options[0]
    if (matched) {
      applyBranchSelection(member, matched.id)
    }
  })
}

async function refreshMemberBranches(repositoryId: number) {
  await repositoryStore.refreshBranches(repositoryId)
  projectSpaceStore.form.memberBranches
    .filter((member) => member.repositoryId === repositoryId && !member.branchId)
    .forEach((member) => {
      const matched = branchOptions(repositoryId).find((branch) => branch.name === member.branchName)
      if (matched) {
        applyBranchSelection(member, matched.id)
      }
    })
}

function handleBranchChange(
  member: typeof projectSpaceStore.form.memberBranches[number],
  value: number | string | null | undefined
) {
  const parsed = typeof value === 'string' ? Number(value) : value
  applyBranchSelection(member, parsed != null && Number.isFinite(parsed) ? parsed : null)
}

function handleBranchDropdown(visible: boolean, repositoryId: number) {
  if (visible) {
    void repositoryStore.fetchBranches(repositoryId)
  }
}

function applyBranchSelection(
  member: typeof projectSpaceStore.form.memberBranches[number],
  branchId: number | null | undefined
) {
  member.branchId = branchId ?? null
  const branch = branchId == null
    ? null
    : branchOptions(member.repositoryId).find((item) => item.id === branchId) ?? null
  if (!branch) {
    member.branchName = ''
    member.branchRefName = null
    member.branchSourceKind = null
    member.commitSha = null
    return
  }
  member.branchName = branch.name
  member.branchRefName = branch.refName
  member.branchSourceKind = branch.sourceKind
  member.commitSha = branch.commitSha
}

watch(
  () => projectStore.selectedProjectId,
  (projectId) => {
    if (!isDeriveMode.value && props.projectId == null && projectId && projectId !== projectSpaceStore.form.projectId) {
      void selectProject(projectId)
    }
  },
  { immediate: true }
)

watch(
  () => [props.projectId, projectStore.projects.length] as const,
  ([projectId]) => {
    if (!isDeriveMode.value && projectId && projectId !== projectSpaceStore.form.projectId) {
      void selectProject(projectId)
    }
  },
  { immediate: true }
)

watch(
  () => suggestedSpaceName.value,
  (name) => {
    if (!isDeriveMode.value && name && !nameTouched.value) {
      projectSpaceStore.form.name = name
    }
  },
  { immediate: true }
)

watch(
  () => [props.sourceSpaceId, sourceSpace.value?.id, projectSpaceStore.spaces.length] as const,
  async ([sourceId]) => {
    if (!isDeriveMode.value || sourceId == null || prefilledSourceId.value === sourceId) return
    const space = sourceSpace.value
    if (!space) return

    projectSpaceStore.form.projectId = space.projectId
    projectSpaceStore.form.name = suggestedCopyName(space.name)
    projectSpaceStore.form.description = space.description ?? ''
    projectSpaceStore.form.defaultBranch = ''
    await projectStore.fetchMembers(space.projectId)
    await projectSpaceStore.fetchMembers(sourceId)
    projectSpaceStore.form.memberBranches = projectSpaceStore.members.map((member) => ({
      repositoryId: member.repositoryId,
      repositoryName: member.repositoryName ?? member.repository ?? '',
      alias: member.alias,
      role: member.role,
      branchId: member.branchId,
      branchName: member.branchName,
      branchRefName: member.branchRefName,
      branchSourceKind: member.branchSourceKind,
      commitSha: member.commitSha,
    }))
    await hydrateBranchOptions()
    prefilledSourceId.value = sourceId
  },
  { immediate: true }
)

async function handleCreateAndIndex() {
  if (!isFormValid.value) {
    ElMessage.warning('请完成所有必填项')
    return
  }
  try {
    await ElMessageBox.confirm(
      isDeriveMode.value ? '确认创建新版本并开始索引？' : '确认创建分析空间并开始索引？',
      '创建确认',
      { confirmButtonText: '确认创建', cancelButtonText: '取消', type: 'info' }
    )
  } catch {
    return
  }

  if (projectSpaceStore.form.projectId && projectSpaceStore.form.memberBranches.length === 0) {
    await projectStore.fetchMembers(projectSpaceStore.form.projectId)
    projectSpaceStore.generateMembers(projectStore.members)
  }

  takeSnapshot()
  creatingAndIndexing.value = true
  const space = await projectSpaceStore.create()
  if (!space) {
    creatingAndIndexing.value = false
    return
  }
  const updated = await projectSpaceStore.prepareAndIndex(space.id)
  creatingAndIndexing.value = false
  if (updated?.status === 'READY') {
    ElMessage.success(isDeriveMode.value ? '新分析空间已创建并可提问' : '分析空间已创建并可提问')
    clearDraft()
    router.push({ name: 'project-detail', params: { projectId: updated.projectId }, query: { spaceId: String(updated.id) } })
  } else if (updated) {
    ElMessage.warning('分析空间已创建，但尚未完成索引，请查看状态')
    router.push({ name: 'project-detail', params: { projectId: updated.projectId }, query: { spaceId: String(updated.id) } })
  }
}

function openSpace(spaceId: number) {
  const space = projectSpaceStore.spaces.find((item) => item.id === spaceId)
  if (!space) return
  router.push({ name: 'project-detail', params: { projectId: space.projectId }, query: { spaceId: String(space.id) } })
}

async function selectSpace(spaceId: number) {
  await projectSpaceStore.fetchMembers(spaceId)
}

async function prepareAndIndexSpace(spaceId: number) {
  try {
    await ElMessageBox.confirm('确认准备代码并执行索引？此操作可能需要一些时间。', '索引确认', {
      confirmButtonText: '开始索引',
      cancelButtonText: '取消',
      type: 'info',
    })
  } catch {
    return
  }
  const updated = await projectSpaceStore.prepareAndIndex(spaceId)
  if (updated) {
    if (updated.status === 'READY') {
      ElMessage.success('分析空间已可提问')
    } else {
      ElMessage.warning('分析空间尚未完成索引，请查看状态')
    }
  }
}

async function refreshSpace(spaceId: number) {
  const updated = await projectSpaceStore.refresh(spaceId)
  if (!updated) return
  if (updated.status === 'STALE') {
    ElMessage.warning('项目空间已过期，请重新准备代码并索引')
  } else {
    ElMessage.success('项目空间状态已刷新')
  }
}

async function deleteSpace(spaceId: number) {
  try {
    await ElMessageBox.confirm(
      '删除该项目空间？不会删除底层仓库和 worktree。此操作不可恢复。',
      '删除确认',
      { confirmButtonText: '确认删除', cancelButtonText: '取消', type: 'warning' }
    )
    const ok = await projectSpaceStore.remove(spaceId)
    if (ok) ElMessage.success('项目空间已删除')
  } catch {
    // 用户取消
  }
}

async function restoreFromDraft() {
  const data = restoreDraft()
  if (data) {
    Object.assign(projectSpaceStore.form, data)
    ElMessage.success('已恢复草稿')
  }
}

function handleReset() {
  ElMessageBox.confirm('确认重置所有配置项？未保存的修改将丢失。', '重置确认', {
    confirmButtonText: '确认重置',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    projectSpaceStore.form.projectId = null
    projectSpaceStore.form.name = ''
    projectSpaceStore.form.description = ''
    projectSpaceStore.form.defaultBranch = ''
    projectSpaceStore.form.memberBranches = []
    clearDraft()
    clearHistory()
    ElMessage.success('已重置')
  }).catch(() => {})
}
</script>

<template>
  <!-- 分析空间配置 -->
  <section class="surface-panel settings-block config-section">
    <div class="section-heading">
      <div>
        <p class="kicker">{{ isDeriveMode ? '创建新版本' : '3. 配置分支并创建分析空间' }}</p>
        <h2>
          {{
            isDeriveMode
              ? '基于当前空间的仓库分支创建新分析空间'
              : selectedProject ? `基于 ${selectedProject.name} 创建可提问空间` : '选择项目后创建可提问空间'
          }}
        </h2>
      </div>
      <el-button
        circle
        :loading="projectSpaceStore.loading"
        title="刷新空间"
        aria-label="刷新空间"
        @click="projectSpaceStore.fetch"
      >
        <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
      </el-button>
    </div>

    <div v-if="selectedProject" class="selected-project-summary">
      <div>
        <span>{{ isDeriveMode ? '来源项目' : '基于当前项目' }}</span>
        <strong>{{ selectedProject.name }}</strong>
      </div>
      <el-tag type="info">{{ projectSpaceStore.form.memberBranches.length }} 个仓库参与分析</el-tag>
    </div>

    <template v-if="selectedProject">
      <!-- 空间基础信息 -->
      <div class="config-group">
        <h3 class="config-group-title">基础信息</h3>
      <div class="settings-form-grid settings-form-grid-repo">
        <div class="config-field">
          <label class="field-label">
            {{ isDeriveMode ? '新空间名称' : '空间名称' }} <span class="required-star">*</span>
          </label>
          <el-input
            v-model="projectSpaceStore.form.name"
            :placeholder="isDeriveMode ? '必须使用一个新的空间名称' : '例如 qys-skill-master'"
            maxlength="120"
            clearable
            show-word-limit
            :class="{ 'is-error': spaceNameError }"
            @input="nameTouched = true"
          />
          <p v-if="spaceNameError" class="field-error">{{ spaceNameError }}</p>
        </div>
        <div class="config-field">
          <label class="field-label">默认分支</label>
          <el-input
            v-model="projectSpaceStore.form.defaultBranch"
            placeholder="为空时使用各仓库默认分支"
            maxlength="255"
            clearable
          />
        </div>
        <div class="config-field span-2">
          <label class="field-label">描述</label>
          <el-input v-model="projectSpaceStore.form.description" placeholder="可选，例如 线上 master 分析空间" clearable />
        </div>
      </div>
    </div>

    <!-- 仓库分支配置 -->
    <div class="config-group">
      <h3 class="config-group-title">仓库分支</h3>
      <p class="config-group-desc">{{ selectedProjectMemberText }}</p>
      <el-table class="workspace-table" :data="projectSpaceStore.form.memberBranches" empty-text="选择项目后自动生成仓库分支" max-height="360">
        <el-table-column prop="repositoryName" label="仓库" min-width="140" />
        <el-table-column label="目录别名" min-width="140">
          <template #default="{ row }">
            <span>{{ row.alias }}</span>
          </template>
        </el-table-column>
        <el-table-column label="分支" min-width="180">
          <template #default="{ row }">
            <el-select
              v-model="row.branchId"
              filterable
              clearable
              placeholder="选择已发现分支"
              :loading="repositoryStore.branchLoadingId === row.repositoryId || repositoryStore.branchRefreshingId === row.repositoryId"
              @change="handleBranchChange(row, $event)"
              @visible-change="handleBranchDropdown($event, row.repositoryId)"
            >
              <el-option
                v-for="branch in branchOptions(row.repositoryId)"
                :key="branch.id"
                :label="branchOptionLabel(branch)"
                :value="branch.id"
              >
                <div class="branch-option">
                  <span>{{ branch.name }}</span>
                  <small>{{ repositoryStore.shortSha(branch.commitSha) }} · {{ repositoryStore.sourceKindLabel(branch.sourceKind) }}</small>
                </div>
              </el-option>
            </el-select>
            <div v-if="row.branchRefName" class="branch-meta">
              <code>{{ row.branchRefName }}</code>
              <span>{{ repositoryStore.shortSha(row.commitSha) }}</span>
            </div>
            <div v-else class="branch-meta muted">
              <span>{{ row.branchName ? `旧配置：${row.branchName}` : '请刷新并选择分支' }}</span>
              <el-button text size="small" @click="refreshMemberBranches(row.repositoryId)">刷新分支</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>
    </template>

    <div v-else class="empty-box compact-empty">
      <p>请先在上一步选择项目，再配置分析空间。</p>
    </div>

    <div class="settings-actions">
      <el-button title="重置表单" aria-label="重置表单" :disabled="creatingAndIndexing || projectSpaceStore.loading" @click="handleReset">
        <RotateCcw class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        重置
      </el-button>
      <el-button
        type="primary"
        :disabled="!isFormValid"
        :loading="creatingAndIndexing || projectSpaceStore.loading"
        @click="handleCreateAndIndex"
      >
        <DatabaseZap class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        {{ isDeriveMode ? '创建新版本并索引' : '创建并索引分析空间' }}
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

    <el-alert
      v-if="projectSpaceStore.error"
      type="error"
      :title="projectSpaceStore.error"
      show-icon
      :closable="false"
    />
  </section>

  <!-- 已有空间管理 -->
  <section v-if="!isDeriveMode" class="surface-panel settings-block existing-space-panel config-section">
    <div class="existing-space-header" @click="showExistingSpaces = !showExistingSpaces">
      <span>
        <span class="kicker">已有分析空间</span>
        <strong>查看已创建空间和维护操作</strong>
      </span>
      <span class="existing-space-count">{{ filteredSpaces.length }} 个空间</span>
    </div>

    <el-collapse-transition>
      <div v-if="showExistingSpaces">

    <div class="config-search-bar">
      <el-input
        v-model="spaceSearchQuery"
        :placeholder="projectId ? '搜索空间名称或状态...' : '搜索空间名称、项目或状态...'"
        :prefix-icon="Search"
        clearable
        size="default"
      />
    </div>

    <el-table v-loading="projectSpaceStore.loading" :data="filteredSpaces" empty-text="暂无项目空间" max-height="400">
      <el-table-column prop="name" label="空间" min-width="160" />
      <el-table-column v-if="!projectId" prop="project" label="项目" min-width="140" />
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-tag :type="projectSpaceStore.statusType(row.status)">{{ projectSpaceStore.statusLabel(row.status) }}</el-tag>
          <div v-if="projectSpaceStore.indexingId === row.id && projectSpaceStore.indexProgress">
            <el-progress :percentage="projectSpaceStore.indexProgress.percent" :stroke-width="4" />
            <span class="index-progress-mini">{{ projectSpaceStore.indexProgress.message }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="lastIndexedAt" label="最近索引" min-width="170">
        <template #default="{ row }">
          {{ formatTime(row.lastIndexedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="232" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-button
              v-if="row.status === 'READY'"
              size="small"
              circle
              type="primary"
              title="进入提问"
              aria-label="进入提问"
              @click="openSpace(row.id)"
            >
              <MessageSquare aria-hidden="true" :size="15" :stroke-width="1.8" />
            </el-button>
            <el-button
              v-if="row.status !== 'READY'"
              size="small"
              circle
              type="primary"
              :loading="projectSpaceStore.preparingId === row.id || projectSpaceStore.indexingId === row.id"
              :disabled="row.status === 'PREPARING' || row.status === 'INDEXING'"
              title="准备并索引"
              aria-label="准备并索引"
              @click="prepareAndIndexSpace(row.id)"
            >
              <DatabaseZap aria-hidden="true" :size="15" :stroke-width="1.8" />
            </el-button>
            <el-button
              size="small"
              circle
              :loading="projectSpaceStore.refreshingId === row.id"
              :disabled="row.status === 'PREPARING' || row.status === 'INDEXING'"
              title="刷新状态"
              aria-label="刷新状态"
              @click="refreshSpace(row.id)"
            >
              <RefreshCw aria-hidden="true" :size="15" :stroke-width="1.8" />
            </el-button>
            <el-button
              size="small"
              circle
              title="查看成员"
              aria-label="查看成员"
              @click="selectSpace(row.id)"
            >
              <Eye aria-hidden="true" :size="15" :stroke-width="1.8" />
            </el-button>
            <el-button
              size="small"
              circle
              type="danger"
              plain
              :loading="projectSpaceStore.deletingId === row.id"
              :disabled="row.status === 'PREPARING' || row.status === 'INDEXING'"
              title="删除空间"
              aria-label="删除空间"
              @click="deleteSpace(row.id)"
            >
              <Trash2 aria-hidden="true" :size="15" :stroke-width="1.8" />
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
      </div>
    </el-collapse-transition>
  </section>

  <!-- 空间成员 -->
  <section v-if="!isDeriveMode && projectSpaceStore.selectedSpace && showExistingSpaces" class="surface-panel settings-block config-section">
    <div class="section-heading">
      <div>
        <p class="kicker">空间成员</p>
        <h2>{{ projectSpaceStore.selectedSpace.name }}</h2>
      </div>
    </div>

    <el-table :data="projectSpaceStore.members" empty-text="暂无成员" max-height="360">
      <el-table-column type="expand" width="44">
        <template #default="{ row }">
          <div class="member-commit-history">
            <strong>{{ row.repositoryName }} 最近 5 次真实提交</strong>
            <ol v-if="row.recentCommits?.length" class="member-commit-list">
              <li v-for="commit in row.recentCommits" :key="commit.commitSha">
                <code>{{ commit.shortSha }}</code>
                <span>{{ commit.commitMessage || '无 Commit Message' }}</span>
              </li>
            </ol>
            <p v-else>暂无提交记录</p>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="repositoryName" label="仓库" min-width="140" />
      <el-table-column prop="alias" label="目录别名" min-width="140" />
      <el-table-column prop="branchName" label="真实分支" min-width="160" />
      <el-table-column prop="commitMessage" label="当前 Commit Message" min-width="220">
        <template #default="{ row }">
          <el-tooltip v-if="row.commitMessage" :content="row.commitSha ?? ''" placement="top">
            <span>{{ row.commitMessage }}</span>
          </el-tooltip>
          <span v-else>{{ row.commitSha ? row.commitSha.slice(0, 7) : '未准备' }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="linkPath" label="项目空间路径" min-width="260" show-overflow-tooltip />
    </el-table>
  </section>
</template>

<style scoped>
.config-group {
  margin: var(--spacing-4) 0;
}

.config-group-title {
  margin: 0 0 4px;
  font-size: var(--font-size-lg);
  color: var(--text);
}

.config-group-desc {
  margin: 0 0 var(--spacing-3);
  font-size: var(--font-size-sm);
  color: var(--muted);
}

.config-field {
  display: grid;
  gap: var(--spacing-1);
}

.required-star {
  color: var(--danger, #dc2626);
  font-weight: 700;
}

.member-commit-history {
  padding: var(--spacing-3) var(--spacing-4);
  background: var(--surface-muted);
}

.member-commit-history strong {
  display: block;
  margin-bottom: var(--spacing-2);
}

.member-commit-list {
  display: grid;
  gap: var(--spacing-2);
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.member-commit-list li {
  display: grid;
  grid-template-columns: 72px minmax(160px, 1fr);
  gap: var(--spacing-2);
}

.field-error {
  margin: 2px 0 0;
  color: var(--danger, #dc2626);
  font-size: 12px;
  line-height: 1.4;
}

.branch-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
}

.branch-option small {
  color: var(--muted);
  font-size: 12px;
}

.branch-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  min-width: 0;
  margin-top: 6px;
  color: var(--muted);
  font-size: 12px;
}

.branch-meta code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.branch-meta.muted {
  justify-content: space-between;
}

.config-search-bar {
  margin-bottom: var(--spacing-4);
}

.index-progress-mini {
  font-size: 11px;
  color: var(--muted);
  display: block;
  margin-top: 4px;
}

.selected-project-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface);
  box-shadow: var(--shadow-soft);
  padding: var(--spacing-3) var(--spacing-4);
}

.selected-project-summary span {
  display: block;
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.selected-project-summary strong {
  display: block;
  margin-top: 3px;
  color: var(--text);
}

.existing-space-panel {
  padding: 0;
  overflow: hidden;
  background: var(--surface-soft, var(--surface));
  border-color: transparent;
}

.existing-space-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-4);
  padding: var(--spacing-5);
  cursor: pointer;
}

.existing-space-header strong {
  display: block;
  margin-top: 4px;
  font-size: var(--font-size-xl);
}

.existing-space-count {
  color: var(--muted);
  font-size: var(--font-size-sm);
  white-space: nowrap;
}

.existing-space-panel .el-table {
  border-top: 1px solid var(--stroke);
}

.config-section {
  border-radius: var(--radius-xl);
  transition:
    border-color var(--transition-normal),
    box-shadow var(--transition-normal);
}

@media (max-width: 900px) {
  .selected-project-summary {
    flex-direction: column;
    align-items: flex-start;
  }
  .settings-form-grid-repo {
    grid-template-columns: 1fr;
  }
  .config-group {
    padding: var(--spacing-3);
  }
}
</style>
