<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowLeft,
  ArrowRight,
  BrainCircuit,
  DatabaseZap,
  GitPullRequest,
  Layers3,
  MessageSquare,
  Pencil,
  Plus,
  RefreshCw,
  Trash2,
} from 'lucide-vue-next'
import { useProjectStore } from '../stores/project'
import { useProjectSpaceStore } from '../stores/projectSpace'
import { useQuestionStore } from '../stores/question'
import type { QuestionRecord } from '../types/question'
import type { ProjectSpaceStatus } from '../types/projectSpace'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const projectSpaceStore = useProjectSpaceStore()
const questionStore = useQuestionStore()
const selectedSpaceId = ref<number | null>(null)
const spaceSearch = ref('')
const spaceStatusFilter = ref<'all' | 'ready' | 'working' | 'maintenance' | 'failed'>('all')
const spaceSort = ref<'updated_desc' | 'name_asc' | 'status'>('updated_desc')
const detailTab = ref<'overview' | 'branches' | 'maintenance'>('overview')

const projectId = computed(() => Number(route.params.projectId))
const routeSpaceId = computed(() => {
  const value = route.query.spaceId
  const raw = Array.isArray(value) ? value[0] : value
  const id = raw ? Number(raw) : null
  return id && !Number.isNaN(id) ? id : null
})
const project = computed(() =>
  projectStore.projects.find((item) => item.id === projectId.value) ?? null
)
const projectSpaces = computed(() =>
  projectSpaceStore.spaces.filter((item) => item.projectId === projectId.value)
)
const readySpaces = computed(() => projectSpaces.value.filter((item) => item.status === 'READY'))
const space = computed(() =>
  projectSpaces.value.find((item) => item.id === selectedSpaceId.value) ?? projectSpaces.value[0] ?? null
)
const questionCountBySpace = computed(() => {
  const result = new Map<number, number>()
  questionStore.conversationHistory.forEach((item) => {
    if (item.projectSpaceId == null) return
    result.set(item.projectSpaceId, (result.get(item.projectSpaceId) ?? 0) + 1)
  })
  return result
})
const filteredProjectSpaces = computed(() => {
  const keyword = spaceSearch.value.trim().toLowerCase()
  const statusRank = {
    READY: 0,
    INDEXING: 1,
    PREPARING: 2,
    READY_TO_INDEX: 3,
    STALE: 4,
    FAILED: 5,
    CREATED: 6,
  }
  return projectSpaces.value
    .filter((item) => {
      if (keyword) {
        const haystack = `${item.name} ${item.description ?? ''}`.toLowerCase()
        if (!haystack.includes(keyword)) return false
      }
      switch (spaceStatusFilter.value) {
        case 'ready':
          return item.status === 'READY'
        case 'working':
          return item.status === 'PREPARING' || item.status === 'INDEXING' || item.status === 'READY_TO_INDEX'
        case 'maintenance':
          return item.status === 'STALE' || item.status === 'CREATED'
        case 'failed':
          return item.status === 'FAILED'
        default:
          return true
      }
    })
    .sort((a, b) => {
      if (spaceSort.value === 'name_asc') return a.name.localeCompare(b.name)
      if (spaceSort.value === 'status') return statusRank[a.status] - statusRank[b.status]
      return new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime()
    })
})
const spaceConversations = computed(() => {
  if (!space.value) return []
  const groups = new Map<number, QuestionRecord[]>()
  questionStore.conversationHistory
    .filter((item) => item.projectSpaceId === space.value?.id)
    .forEach((item) => {
      const key = item.conversationId ?? -item.id
      const list = groups.get(key) ?? []
      list.push(item)
      groups.set(key, list)
    })

  const summaries = Array.from(groups.entries())
    .map(([conversationId, items]) => {
      const sorted = [...items].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
      const latest = sorted[0]
      return {
        conversationId,
        questionId: latest.id,
        title: latest.conversationTitle ?? latest.text,
        count: sorted.length,
        lastActiveAt: latest.createdAt,
      }
    })
    .sort((a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime())

  const merged = new Map<string, (typeof summaries)[number] & { conversationCount: number }>()
  summaries.forEach((item) => {
    const key = item.title.trim()
    const existing = merged.get(key)
    if (!existing) {
      merged.set(key, { ...item, conversationCount: 1 })
      return
    }
    existing.conversationCount += 1
    existing.count += item.count
    if (new Date(item.lastActiveAt).getTime() > new Date(existing.lastActiveAt).getTime()) {
      existing.conversationId = item.conversationId
      existing.questionId = item.questionId
      existing.lastActiveAt = item.lastActiveAt
    }
  })

  return Array.from(merged.values())
    .sort((a, b) => new Date(b.lastActiveAt).getTime() - new Date(a.lastActiveAt).getTime())
})

const totalQuestionCount = computed(() =>
  questionStore.conversationHistory.filter((item) => item.projectSpaceId === space.value?.id).length
)
const projectQuestionCount = computed(() => {
  const ids = new Set(projectSpaces.value.map((item) => item.id))
  return questionStore.conversationHistory.filter(
    (item) => item.projectSpaceId != null && ids.has(item.projectSpaceId)
  ).length
})
const latestConversation = computed(() => spaceConversations.value[0] ?? null)
const staleMembers = computed(() =>
  projectSpaceStore.members.filter((member) => member.behindRemote)
)
const indexableSpaceStatuses = new Set<ProjectSpaceStatus>(['READY_TO_INDEX', 'READY', 'STALE'])
// 增量索引仅对已索引过的空间可用（READY/STALE）
const incrementalIndexableStatuses = new Set<ProjectSpaceStatus>(['READY', 'STALE'])
// 重新索引作为恢复路径，额外放行 FAILED
const reindexableSpaceStatuses = new Set<ProjectSpaceStatus>([
  'READY_TO_INDEX',
  'READY',
  'STALE',
  'FAILED',
])

watch(
  [projectId, projectSpaces, routeSpaceId],
  ([id, spaces, querySpaceId]) => {
    if (!id || Number.isNaN(id)) {
      router.push('/projects')
      return
    }
    if (!project.value) return

    const querySpace = spaces.find((item) => item.id === querySpaceId)
    const current = spaces.find((item) => item.id === selectedSpaceId.value)
    const target = querySpace ?? current ?? spaces.find((item) => item.status === 'READY') ?? spaces[0] ?? null
    selectedSpaceId.value = target?.id ?? null
  },
  { immediate: true }
)

watch(
  () => space.value?.id,
  async (id) => {
    if (!id) {
      questionStore.form.projectSpaceId = null
      return
    }
    projectSpaceStore.selectedSpaceId = id
    await projectSpaceStore.fetchMembers(id)
    questionStore.startNewQuestion()
    questionStore.form.projectSpaceId = space.value?.status === 'READY' ? id : null
  },
  { immediate: true }
)

watch(
  () => space.value?.status,
  (status) => {
    questionStore.form.projectSpaceId = status === 'READY' && space.value ? space.value.id : null
  },
  { immediate: true }
)

function selectAnalysisSpace(id: number) {
  selectedSpaceId.value = id
  detailTab.value = 'overview'
}

function questionCountOf(spaceId: number) {
  return questionCountBySpace.value.get(spaceId) ?? 0
}

function openChatWorkspace() {
  questionStore.form.projectSpaceId = space.value?.status === 'READY' ? space.value.id : null
  if (!space.value) return
  router.push({ name: 'chat', query: { spaceId: String(space.value.id) } })
}

function formatHistoryTime(dateStr: string) {
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function formatCommitTime(dateStr: string | null) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function canIndexSpace(status: ProjectSpaceStatus) {
  return indexableSpaceStatuses.has(status)
}

function canIncrementalIndex(status: ProjectSpaceStatus) {
  return incrementalIndexableStatuses.has(status)
}

function canReindexSpace(status: ProjectSpaceStatus) {
  return reindexableSpaceStatuses.has(status)
}

async function prepareAndIndexSpace() {
  if (!space.value) return
  const updated = await projectSpaceStore.prepareAndIndex(space.value.id)
  if (updated?.status === 'READY') {
    questionStore.form.projectSpaceId = updated.id
    ElMessage.success('分析空间已可提问')
  } else if (updated) {
    ElMessage.warning('分析空间尚未完成索引，请查看状态')
  }
}

async function indexSpace() {
  if (!space.value) return
  const updated = await projectSpaceStore.index(space.value.id)
  if (updated?.status === 'READY') {
    questionStore.form.projectSpaceId = updated.id
    ElMessage.success('分析空间索引已完成')
  } else if (updated) {
    questionStore.form.projectSpaceId = null
    ElMessage.warning('分析空间尚未完成索引，请查看状态')
  }
}

async function reindexSpace() {
  if (!space.value) return
  const updated = await projectSpaceStore.reindex(space.value.id)
  if (updated?.status === 'READY') {
    questionStore.form.projectSpaceId = updated.id
    ElMessage.success('分析空间已重新索引完成')
  } else if (updated) {
    questionStore.form.projectSpaceId = null
    ElMessage.warning('分析空间尚未完成重新索引，请查看状态')
  }
}

async function refreshSpace() {
  if (!space.value) return
  const updated = await projectSpaceStore.refresh(space.value.id)
  await projectSpaceStore.fetchMembers(space.value.id)
  if (updated?.status === 'STALE') {
    questionStore.form.projectSpaceId = null
    ElMessage.warning('项目空间已过期，请重新准备代码并索引')
  } else if (staleMembers.value.length) {
    ElMessage.warning('已刷新，当前空间有成员落后远端分支')
  } else if (updated) {
    ElMessage.success('项目空间状态已刷新')
  }
}

async function pullSpace() {
  if (!space.value) return
  const updated = await projectSpaceStore.pullRemote(space.value.id)
  if (updated?.status === 'STALE' || staleMembers.value.length) {
    questionStore.form.projectSpaceId = null
    ElMessage.warning('已拉取远端代码，当前空间落后远端，请重新准备并索引')
  } else if (updated) {
    ElMessage.success('已拉取远端代码，当前空间为最新')
  }
}

async function deleteSpace() {
  if (!space.value) return
  const deletedId = space.value.id
  const ok = await projectSpaceStore.remove(deletedId)
  if (ok) {
    ElMessage.success('项目空间已删除')
    const next = projectSpaces.value.find((item) => item.id !== deletedId) ?? null
    selectedSpaceId.value = next?.id ?? null
  }
}
</script>

<template>
  <section v-if="project" class="project-detail-page">
    <div class="project-detail-nav">
      <router-link to="/projects">
        <el-button text>
          <ArrowLeft class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          返回项目空间
        </el-button>
      </router-link>
    </div>

    <main class="project-detail-main">
      <section class="surface-panel project-command-panel">
        <div class="project-command-copy">
          <div class="project-command-title">
            <div>
              <p class="kicker">项目空间</p>
              <h1>{{ project.name }}</h1>
            </div>
          </div>
          <p class="project-page-copy">
            {{ project.description || '选择下方分析空间查看分支组合、索引状态和维护操作。' }}
          </p>
        </div>
        <div class="project-detail-actions">
          <div class="project-command-metrics" aria-label="项目空间统计">
            <div class="project-command-metric">
              <span>分析空间</span>
              <strong>{{ projectSpaces.length }}</strong>
              <em>{{ readySpaces.length }} 个可提问</em>
            </div>
            <div class="project-command-metric">
              <span>累计对话</span>
              <strong>{{ projectQuestionCount }}</strong>
              <em>该项目累计问答</em>
            </div>
          </div>
          <el-tag :type="readySpaces.length > 0 ? 'success' : 'info'" class="project-ready-tag">
            {{ readySpaces.length }} / {{ projectSpaces.length }} 可分析
          </el-tag>
          <router-link :to="`/projects/${projectId}/analysis-spaces/new`">
            <el-button type="primary">
              <Plus class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
              创建分析空间
            </el-button>
          </router-link>
        </div>
      </section>

      <section class="surface-panel project-space-switch-panel">
        <div class="section-heading compact space-console-heading">
          <div>
            <h3>分析空间</h3>
            <p>
              共 {{ projectSpaces.length }} 个分析空间，当前显示 {{ filteredProjectSpaces.length }} 个。
            </p>
          </div>
        </div>
        <div class="space-console-toolbar">
          <el-input
            v-model="spaceSearch"
            clearable
            placeholder="搜索空间名或描述"
            class="space-search-input"
          />
          <el-select v-model="spaceStatusFilter" class="space-filter-select" aria-label="状态筛选">
            <el-option label="全部状态" value="all" />
            <el-option label="可提问" value="ready" />
            <el-option label="处理中" value="working" />
            <el-option label="需维护" value="maintenance" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-select v-model="spaceSort" class="space-filter-select" aria-label="排序方式">
            <el-option label="最近更新" value="updated_desc" />
            <el-option label="名称 A-Z" value="name_asc" />
            <el-option label="状态优先" value="status" />
          </el-select>
        </div>

        <div v-if="projectSpaces.length && filteredProjectSpaces.length" class="space-console-list">
          <div class="space-console-header" aria-hidden="true">
            <span>分析空间</span>
            <span>状态</span>
            <span>对话</span>
            <span>更新时间</span>
            <span>操作</span>
          </div>
          <button
            v-for="item in filteredProjectSpaces"
            :key="item.id"
            type="button"
            class="space-console-row"
            :class="{ 'is-ready': item.status === 'READY', 'is-current': item.id === space?.id }"
            @click="selectAnalysisSpace(item.id)"
          >
            <span class="space-console-name">
              <strong>{{ item.name }}</strong>
              <span>{{ item.description || '暂无描述' }}</span>
            </span>
            <span class="space-console-status">
              <el-tag v-if="item.id === space?.id" size="small" type="info">当前</el-tag>
              <el-tag size="small" :type="projectSpaceStore.statusType(item.status)">
                {{ projectSpaceStore.statusLabel(item.status) }}
              </el-tag>
            </span>
            <span class="space-console-count">{{ questionCountOf(item.id) }}</span>
            <span class="space-console-time">{{ formatCommitTime(item.updatedAt) }}</span>
            <span class="space-console-action">
              {{ item.status === 'READY' ? '进入聊天' : '查看维护' }}
              <span aria-hidden="true">→</span>
            </span>
          </button>
        </div>
        <div v-else-if="projectSpaces.length" class="space-console-empty">
          没有匹配的分析空间，换个关键词或筛选条件试试。
        </div>
        <router-link v-else class="project-space-empty-card" :to="`/projects/${projectId}/analysis-spaces/new`">
          创建该项目空间的第一个分析空间
          <ArrowRight aria-hidden="true" :size="16" :stroke-width="1.8" />
        </router-link>
      </section>

      <section v-if="space" class="surface-panel selected-space-panel">
        <div class="selected-space-head">
          <div>
            <p class="kicker">当前选中空间</p>
            <h2>{{ space.name }}</h2>
            <p class="project-page-copy">
              {{ space.status === 'READY' ? '空间已完成索引，可直接进入聊天工作台。' : '该分析空间需要准备代码并完成索引后才能提问。' }}
            </p>
          </div>
          <div class="project-detail-actions">
            <el-tag :type="projectSpaceStore.statusType(space.status)">
              {{ projectSpaceStore.statusLabel(space.status) }}
            </el-tag>
            <router-link :to="`/project-spaces/${space.id}/self-learning`">
              <el-button>
                <BrainCircuit class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
                自学习管理
              </el-button>
            </router-link>
            <el-button
              v-if="space.status === 'READY'"
              type="primary"
              @click="openChatWorkspace"
            >
              <MessageSquare class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
              进入聊天工作台
            </el-button>
            <el-button
              v-if="space.status !== 'READY'"
              type="primary"
              :loading="projectSpaceStore.preparingId === space.id || projectSpaceStore.indexingId === space.id"
              :disabled="space.status === 'PREPARING' || space.status === 'INDEXING'"
              @click="prepareAndIndexSpace"
            >
              <DatabaseZap class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
              准备并索引
            </el-button>
          </div>
        </div>

        <div class="selected-space-tabs" role="tablist" aria-label="当前空间详情">
          <button
            type="button"
            :class="{ 'is-active': detailTab === 'overview' }"
            @click="detailTab = 'overview'"
          >
            概览
          </button>
          <button
            type="button"
            :class="{ 'is-active': detailTab === 'branches' }"
            @click="detailTab = 'branches'"
          >
            分支组合
          </button>
          <button
            type="button"
            :class="{ 'is-active': detailTab === 'maintenance' }"
            @click="detailTab = 'maintenance'"
          >
            维护
          </button>
        </div>

        <div v-if="detailTab === 'overview'" class="selected-space-overview">
          <div class="selected-space-stat">
            <span>空间状态</span>
            <strong>{{ projectSpaceStore.statusLabel(space.status) }}</strong>
            <em>{{ space.lastIndexedAt ? `上次索引 ${formatCommitTime(space.lastIndexedAt)}` : '尚未完成索引' }}</em>
          </div>
          <div class="selected-space-stat">
            <span>累计问答</span>
            <strong>{{ totalQuestionCount }}</strong>
            <em>{{ latestConversation ? `最近 ${formatHistoryTime(latestConversation.lastActiveAt)}` : '暂无对话' }}</em>
          </div>
          <div class="selected-space-stat">
            <span>仓库成员</span>
            <strong>{{ projectSpaceStore.members.length }}</strong>
            <em>{{ staleMembers.length ? `${staleMembers.length} 个落后远端` : '分支状态正常' }}</em>
          </div>
          <p v-if="space.lastError" class="space-error">{{ space.lastError }}</p>
          <div v-if="projectSpaceStore.indexingId === space.id && projectSpaceStore.indexProgress" class="index-progress selected-space-progress">
            <el-progress
              :percentage="projectSpaceStore.indexProgress.percent"
              :status="projectSpaceStore.indexProgress.percent === 100 ? 'success' : ''"
            />
            <p class="index-progress-message">{{ projectSpaceStore.indexProgress.message }}</p>
          </div>
        </div>

        <div v-else-if="detailTab === 'maintenance'" class="selected-space-maintenance">
          <div class="maintenance-card maintenance-card-index">
            <div class="maintenance-card-head">
              <h4>索引管理</h4>
              <p>增量索引仅同步代码变更，速度较快；重新索引会删除现有索引并全量重建，耗时较长。</p>
            </div>
            <div class="maintenance-card-body">
              <div class="maintenance-action">
                <el-button
                  type="primary"
                  :loading="projectSpaceStore.indexingId === space.id"
                  :disabled="
                    space.status === 'PREPARING' ||
                    space.status === 'INDEXING' ||
                    !canIncrementalIndex(space.status)
                  "
                  title="增量索引：同步代码变更"
                  aria-label="增量索引"
                  @click="indexSpace"
                >
                  <DatabaseZap class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
                  增量索引
                </el-button>
                <span class="maintenance-action-hint">同步代码变更，快速更新索引</span>
              </div>
              <div class="maintenance-action">
                <el-popconfirm
                  title="确定要重新索引吗？这将删除现有索引并全量重建，重建期间无法提问，可能需要较长时间。"
                  confirm-button-text="重新索引"
                  cancel-button-text="取消"
                  confirm-button-type="danger"
                  :icon="null"
                  @confirm="reindexSpace"
                >
                  <template #reference>
                    <el-button
                      type="danger"
                      plain
                      :loading="projectSpaceStore.indexingId === space.id"
                      :disabled="
                        space.status === 'PREPARING' ||
                        space.status === 'INDEXING' ||
                        !canReindexSpace(space.status)
                      "
                      title="重新索引：删除旧索引并全量重建"
                      aria-label="重新索引"
                    >
                      <Layers3 class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
                      重新索引
                    </el-button>
                  </template>
                </el-popconfirm>
                <span class="maintenance-action-hint">删除旧索引并全量重建</span>
              </div>
            </div>
          </div>

          <div class="maintenance-card">
            <div class="maintenance-card-head">
              <h4>代码同步</h4>
              <p>检查远端分支状态或拉取最新代码，拉取后若空间落后远端需重新准备并索引。</p>
            </div>
            <div class="maintenance-card-body">
              <div class="maintenance-action">
                <el-button
                  :loading="projectSpaceStore.refreshingId === space.id"
                  :disabled="space.status === 'PREPARING' || space.status === 'INDEXING'"
                  title="刷新状态"
                  aria-label="刷新状态"
                  @click="refreshSpace"
                >
                  <RefreshCw class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
                  刷新状态
                </el-button>
                <span class="maintenance-action-hint">检查远端分支是否落后</span>
              </div>
              <div class="maintenance-action">
                <el-button
                  type="primary"
                  plain
                  :loading="projectSpaceStore.pullingId === space.id"
                  :disabled="space.status === 'PREPARING' || space.status === 'INDEXING'"
                  title="拉取最新代码"
                  aria-label="拉取最新代码"
                  @click="pullSpace"
                >
                  <GitPullRequest class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
                  拉取最新代码
                </el-button>
                <span class="maintenance-action-hint">拉取远端最新代码并检查是否需更新</span>
              </div>
            </div>
          </div>

          <div class="maintenance-card maintenance-card-danger">
            <div class="maintenance-card-head">
              <h4>空间管理</h4>
              <p>编辑项目空间配置或删除当前分析空间，删除不会影响底层仓库和 worktree。</p>
            </div>
            <div class="maintenance-card-body">
              <div class="maintenance-action">
                <router-link :to="`/projects/${space.id}/config`">
                  <el-button title="编辑项目配置" aria-label="编辑项目配置">
                    <Pencil class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
                    编辑项目配置
                  </el-button>
                </router-link>
                <span class="maintenance-action-hint">修改空间成员与分支配置</span>
              </div>
              <div class="maintenance-action">
                <el-popconfirm
                  title="删除该分析空间？不会删除底层仓库和 worktree。"
                  confirm-button-text="删除"
                  cancel-button-text="取消"
                  confirm-button-type="danger"
                  @confirm="deleteSpace"
                >
                  <template #reference>
                    <el-button
                      type="danger"
                      plain
                      :loading="projectSpaceStore.deletingId === space.id"
                      :disabled="space.status === 'PREPARING' || space.status === 'INDEXING'"
                      title="删除分析空间"
                      aria-label="删除分析空间"
                    >
                      <Trash2 class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
                      删除
                    </el-button>
                  </template>
                </el-popconfirm>
                <span class="maintenance-action-hint">移除当前分析空间</span>
              </div>
            </div>
          </div>

          <div v-if="projectSpaceStore.indexingId === space.id && projectSpaceStore.indexProgress" class="index-progress">
            <el-progress
              :percentage="projectSpaceStore.indexProgress.percent"
              :status="projectSpaceStore.indexProgress.percent === 100 ? 'success' : ''"
            />
            <p class="index-progress-message">{{ projectSpaceStore.indexProgress.message }}</p>
          </div>
          <p v-if="space.lastError" class="space-error">{{ space.lastError }}</p>
        </div>

        <div v-else class="selected-space-branches">
          <div class="section-heading compact branch-section-heading">
            <div>
              <h3>分支组合</h3>
              <p v-if="staleMembers.length" class="branch-warning">
                {{ staleMembers.length }} 个仓库落后远端分支，请拉取后重新准备并索引。
              </p>
            </div>
            <div class="branch-actions">
              <span class="stat-label">{{ projectSpaceStore.members.length }} 个仓库</span>
              <el-button
                size="small"
                circle
                :loading="projectSpaceStore.refreshingId === space.id"
                title="刷新分支状态"
                aria-label="刷新分支状态"
                @click="refreshSpace"
              >
                <RefreshCw aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
              <el-button
                size="small"
                type="primary"
                plain
                :loading="projectSpaceStore.pullingId === space.id"
                title="拉取最新代码"
                aria-label="拉取最新代码"
                @click="pullSpace"
              >
                <GitPullRequest class="button-icon" aria-hidden="true" :size="15" :stroke-width="1.8" />
                拉取最新代码
              </el-button>
            </div>
          </div>
          <el-table :data="projectSpaceStore.members" empty-text="暂无成员">
            <el-table-column type="expand" width="44">
              <template #default="{ row }">
                <div class="commit-history">
                  <div class="commit-history-heading">
                    <strong>{{ row.repositoryName }}</strong>
                    <span>最近 5 次真实提交</span>
                    <el-tag v-if="row.behindRemote" type="warning" size="small">落后远端</el-tag>
                  </div>
                  <ol v-if="row.recentCommits?.length" class="commit-list">
                    <li v-for="commit in row.recentCommits" :key="commit.commitSha" class="commit-item">
                      <code>{{ commit.shortSha }}</code>
                      <span class="commit-message">{{ commit.commitMessage || '无 Commit Message' }}</span>
                      <span class="commit-time">{{ formatCommitTime(commit.committedAt) }}</span>
                    </li>
                  </ol>
                  <p v-else class="commit-empty">暂无提交记录</p>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="repositoryName" label="仓库" min-width="140" />
            <el-table-column prop="alias" label="目录别名" min-width="120" />
            <el-table-column prop="branchName" label="真实分支" min-width="170">
              <template #default="{ row }">
                <div class="branch-cell">
                  <span>{{ row.branchName }}</span>
                  <el-tag v-if="row.behindRemote" type="warning" size="small">落后远端</el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="commitMessage" label="当前 Commit Message" min-width="240">
              <template #default="{ row }">
                <el-tooltip v-if="row.commitMessage" :content="row.commitSha ?? ''" placement="top">
                  <span>{{ row.commitMessage }}</span>
                </el-tooltip>
                <span v-else>{{ row.commitSha ? row.commitSha.slice(0, 7) : '未准备' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </main>
  </section>
</template>

<style scoped>
.index-progress {
  margin-top: 12px;
  padding: 12px;
  background-color: var(--el-fill-color-light);
  border-radius: 8px;
}

.index-progress-message {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.project-command-metrics {
  display: flex;
  align-items: stretch;
  gap: var(--spacing-3);
  flex-wrap: wrap;
  justify-content: flex-end;
}

.project-command-metric {
  min-width: 126px;
  padding: var(--spacing-3) var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.14);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.72);
  box-shadow: var(--shadow-inset);
}

.project-command-metric span,
.project-command-metric em {
  display: block;
  color: var(--muted);
  font-size: var(--font-size-xs);
  font-style: normal;
  line-height: var(--line-height-normal);
}

.project-command-metric strong {
  display: block;
  margin: var(--spacing-1) 0;
  color: var(--text);
  font-size: 24px;
  line-height: 1;
}

.project-ready-tag {
  align-self: center;
  flex: 0 0 auto;
}

.space-console-heading {
  align-items: flex-start;
}

.space-console-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 160px 160px;
  gap: var(--spacing-3);
  margin-top: var(--spacing-4);
}

.space-search-input,
.space-filter-select {
  min-width: 0;
}

.space-console-list {
  display: grid;
  margin-top: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--surface);
}

.space-console-header,
.space-console-row {
  display: grid;
  grid-template-columns: minmax(220px, 1.4fr) 180px 88px 128px 116px;
  gap: var(--spacing-3);
  align-items: center;
}

.space-console-header {
  padding: var(--spacing-3) var(--spacing-4);
  background: var(--surface-soft);
  color: var(--muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.space-console-row {
  width: 100%;
  min-width: 0;
  padding: var(--spacing-3) var(--spacing-4);
  border: 0;
  border-top: 1px solid var(--stroke);
  background: transparent;
  color: var(--text);
  cursor: pointer;
  text-align: left;
  transition:
    background var(--transition-fast),
    box-shadow var(--transition-fast);
}

.space-console-row:hover {
  background: var(--chat-accent-soft);
}

.space-console-row.is-current {
  background: rgba(79, 110, 247, 0.08);
  box-shadow: inset 3px 0 0 var(--chat-accent);
}

.space-console-name {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.space-console-name strong,
.space-console-name span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-console-name strong {
  color: var(--text);
  font-size: var(--font-size-md);
}

.space-console-name span,
.space-console-time {
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.space-console-status {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  flex-wrap: wrap;
}

.space-console-count {
  color: var(--text);
  font-size: var(--font-size-lg);
  font-weight: var(--font-weight-semibold);
}

.space-console-action {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: var(--spacing-1);
  color: var(--chat-accent);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
}

.space-console-empty {
  margin-top: var(--spacing-4);
  padding: var(--spacing-5);
  border: 1px dashed var(--stroke);
  border-radius: var(--radius-lg);
  color: var(--muted);
  text-align: center;
}

.selected-space-panel {
  display: grid;
  gap: var(--spacing-4);
}

.selected-space-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-4);
  flex-wrap: wrap;
}

.selected-space-head h2 {
  margin: var(--spacing-1) 0 var(--spacing-2);
  font-size: var(--font-size-3xl);
  line-height: var(--line-height-tight);
}

.selected-space-tabs {
  display: inline-flex;
  width: fit-content;
  max-width: 100%;
  padding: var(--spacing-1);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-full);
  background: var(--surface-soft);
  overflow-x: auto;
}

.selected-space-tabs button {
  min-width: 88px;
  padding: var(--spacing-2) var(--spacing-4);
  border: 0;
  border-radius: var(--radius-full);
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-weight: var(--font-weight-semibold);
  transition:
    background var(--transition-fast),
    color var(--transition-fast),
    box-shadow var(--transition-fast);
}

.selected-space-tabs button.is-active {
  background: var(--surface);
  color: var(--chat-accent);
  box-shadow: var(--shadow-soft);
}

.selected-space-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: var(--spacing-3);
}

.selected-space-stat {
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface-soft);
}

.selected-space-stat span,
.selected-space-stat em {
  display: block;
  color: var(--muted);
  font-size: var(--font-size-sm);
  font-style: normal;
}

.selected-space-stat strong {
  display: block;
  margin: var(--spacing-2) 0 var(--spacing-1);
  color: var(--text);
  font-size: var(--font-size-xl);
}

.selected-space-progress {
  grid-column: 1 / -1;
}

.selected-space-maintenance,
.selected-space-branches {
  display: grid;
  gap: var(--spacing-4);
}

.maintenance-card {
  display: grid;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface-soft);
}

.maintenance-card-index {
  border-color: rgba(79, 110, 247, 0.24);
}

.maintenance-card-danger {
  border-color: rgba(220, 38, 38, 0.18);
}

.maintenance-card-head h4 {
  margin: 0 0 var(--spacing-1);
  color: var(--text);
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
}

.maintenance-card-head p {
  margin: 0;
  color: var(--muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.maintenance-card-body {
  display: grid;
  gap: var(--spacing-3);
}

.maintenance-action {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  flex-wrap: wrap;
}

.maintenance-action-hint {
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.commit-history {
  padding: var(--spacing-4);
  background: var(--surface-muted);
  overflow: hidden;
}

.branch-section-heading {
  align-items: flex-start;
  gap: var(--spacing-3);
}

.branch-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  justify-content: flex-end;
  align-items: center;
}

.branch-warning {
  margin: var(--spacing-1) 0 0;
  color: var(--warning, #d97706);
  font-size: var(--font-size-sm);
}

.branch-cell {
  display: flex;
  gap: var(--spacing-2);
  align-items: center;
  flex-wrap: wrap;
}

.commit-history-heading {
  display: flex;
  gap: var(--spacing-2);
  align-items: baseline;
  margin-bottom: var(--spacing-2);
  color: var(--text);
}

.commit-history-heading span,
.commit-time {
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.commit-list {
  display: grid;
  gap: var(--spacing-2);
  margin: 0;
  padding-left: 0;
  list-style: none;
}

.commit-item {
  display: grid;
  grid-template-columns: 96px minmax(0, 1fr) 120px;
  column-gap: var(--spacing-3);
  align-items: start;
  min-width: 0;
}

.commit-item code {
  white-space: nowrap;
  color: var(--muted);
}

.commit-message {
  color: var(--text);
  overflow-wrap: anywhere;
  min-width: 0;
  line-height: 1.5;
  word-break: break-word;
}

.commit-time {
  text-align: right;
  white-space: nowrap;
}

.commit-empty {
  margin: 0;
  color: var(--muted);
}

:global([data-theme="dark"]) .project-command-metric {
  background: rgba(24, 24, 29, 0.72);
}

@media (prefers-color-scheme: dark) {
  :global(:root:not([data-theme="light"])) .project-command-metric {
    background: rgba(24, 24, 29, 0.72);
  }
}

@media (max-width: 900px) {
  .space-console-toolbar {
    grid-template-columns: 1fr;
  }

  .space-console-header {
    display: none;
  }

  .space-console-row {
    grid-template-columns: 1fr;
    gap: var(--spacing-2);
  }

  .space-console-status,
  .space-console-action {
    justify-content: flex-start;
  }

  .selected-space-overview {
    grid-template-columns: 1fr;
  }

  .project-command-metrics,
  .project-detail-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 560px) {
  .project-command-metric {
    flex: 1 1 140px;
  }
}
</style>
