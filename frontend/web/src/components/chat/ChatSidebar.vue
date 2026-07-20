<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ChevronDown, Download, FolderGit2, GitCommitHorizontal, Plus, Search, Trash2, X } from 'lucide-vue-next'
import { useQuestionStore } from '../../stores/question'
import { useProjectStore } from '../../stores/project'
import { useProjectSpaceStore } from '../../stores/projectSpace'
import ChatHistoryItem from './HistoryItem.vue'
import logoUrl from '../../images/logo.svg'

const route = useRoute()
const router = useRouter()
const questionStore = useQuestionStore()
const projectStore = useProjectStore()
const projectSpaceStore = useProjectSpaceStore()
const emit = defineEmits<{ close: [] }>()
const expandedGroups = ref<Record<number, boolean>>({})
const mobileOpen = ref(false)
const deletingId = ref<number | null>(null)
const visibleCount = ref(5)
const spaceInfoExpanded = ref(false)

function toggleMobileSidebar() {
  mobileOpen.value = !mobileOpen.value
}

function closeMobileSidebar() {
  mobileOpen.value = false
  emit('close')
}

function startNewQuestion() {
  questionStore.startNewConversation()
  router.push(chatLocation(null))
  closeMobileSidebar()
}

function backToSpaceDetail() {
  if (!activeSpace.value) return
  router.push({
    name: 'project-detail',
    params: { projectId: activeSpace.value.projectId },
    query: { spaceId: String(activeSpace.value.id) },
  })
  closeMobileSidebar()
}

function enterConversation(conversationId: number) {
  questionStore.enterConversation(conversationId)
  router.push(chatLocation(questionStore.activeQuestion?.id ?? null))
  closeMobileSidebar()
}

function selectQuestion(questionId: number) {
  questionStore.select(questionId)
  router.push(chatLocation(questionId))
  closeMobileSidebar()
}

function toggleGroup(conversationId: number) {
  expandedGroups.value[conversationId] = !expandedGroups.value[conversationId]
}

function isGroupExpanded(conversationId: number) {
  return expandedGroups.value[conversationId] ?? false
}

function formatTime(dateStr: string) {
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60_000) return '刚刚'
  if (diff < 3_600_000) return `${Math.floor(diff / 60_000)} 分钟前`
  if (diff < 86_400_000) return `${Math.floor(diff / 3_600_000)} 小时前`
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

const activeGroupId = computed(() => {
  return questionStore.activeConversationId
})

const activeSpace = computed(() => {
  if (!questionStore.form.projectSpaceId) return null
  return projectSpaceStore.spaces.find(s => s.id === questionStore.form.projectSpaceId) ?? null
})

const activeProject = computed(() => {
  if (!activeSpace.value) return null
  return projectStore.projects.find(project => project.id === activeSpace.value?.projectId) ?? null
})

const activeProjectName = computed(() => activeProject.value?.name ?? activeSpace.value?.project ?? '当前项目')

const primaryMembers = computed(() => projectSpaceStore.members.slice(0, 3))
const primaryMember = computed(() => primaryMembers.value[0] ?? null)

function toggleSpaceInfo() {
  spaceInfoExpanded.value = !spaceInfoExpanded.value
}

function chatQuery(questionId?: number | null) {
  const spaceId = questionStore.form.projectSpaceId ?? activeSpace.value?.id ?? route.query.spaceId
  return {
    ...(spaceId ? { spaceId: String(spaceId) } : {}),
    ...(questionId ? { questionId: String(questionId) } : {}),
  }
}

function chatLocation(questionId?: number | null) {
  return { name: 'chat', query: chatQuery(questionId) }
}

function shortSha(sha?: string | null) {
  return sha ? sha.slice(0, 7) : '未准备'
}

function formatCommitTime(dateStr?: string | null) {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

const searchKeyword = ref('')

const filteredGroups = computed(() => {
  if (!searchKeyword.value.trim()) return questionStore.currentSpaceConversationGroups
  const keyword = searchKeyword.value.toLowerCase()
  return questionStore.currentSpaceConversationGroups
    .map(group => ({
      ...group,
      questions: group.questions.filter(q =>
        q.text?.toLowerCase().includes(keyword) ||
        q.answer?.toLowerCase().includes(keyword)
      ),
      questionCount: group.questions.length,
    }))
    .filter(group => group.questions.length > 0)
})

const visibleGroups = computed(() => filteredGroups.value.slice(0, visibleCount.value))
const hasMoreGroups = computed(() => filteredGroups.value.length > visibleCount.value)

function loadMore() {
  visibleCount.value += 5
}

watch(searchKeyword, () => {
  visibleCount.value = 5
})

watch(
  () => activeSpace.value?.id,
  () => {
    spaceInfoExpanded.value = false
  }
)

async function deleteConversation(conversationId: number) {
  if (deletingId.value !== null) return
  deletingId.value = conversationId
  try {
    await questionStore.deleteConversation(conversationId)
    delete expandedGroups.value[conversationId]
  } finally {
    deletingId.value = null
  }
}

function exportChat() {
  const content = questionStore.exportConversation()
  if (!content) return
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `对话记录_${new Date().toISOString().slice(0, 10)}.md`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar-open': mobileOpen }">
    <div class="brand-block">
      <img class="brand-logo" :src="logoUrl" alt="Ascoder" />
      <button
        class="mobile-close-btn"
        type="button"
        title="关闭侧栏"
        aria-label="关闭侧栏"
        @click="closeMobileSidebar"
      >
        <X aria-hidden="true" :size="16" :stroke-width="1.8" />
      </button>
    </div>

    <button class="primary-action" type="button" title="新建对话" aria-label="新建对话" @click="startNewQuestion">
      <Plus class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
      新对话
    </button>

    <div class="sidebar-space-slot">
      <div v-if="activeSpace" class="space-info" :class="{ 'space-info-expanded': spaceInfoExpanded }">
        <button class="back-space-button" type="button" @click="backToSpaceDetail">
          <ArrowLeft class="button-icon" aria-hidden="true" :size="15" :stroke-width="1.8" />
          返回空间详情
        </button>

        <button
          class="space-info-summary"
          type="button"
          :aria-expanded="spaceInfoExpanded"
          @click="toggleSpaceInfo"
        >
          <FolderGit2 class="space-info-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          <div class="space-info-title">
            <span class="space-info-project">{{ activeProjectName }}</span>
            <span class="space-info-name">{{ activeSpace.name }}</span>
            <span class="space-info-brief">
              {{ projectSpaceStore.members.length }} 个仓库
              <template v-if="primaryMember"> · {{ primaryMember.branchName }}</template>
              <template v-if="primaryMember?.commitSha"> · {{ shortSha(primaryMember.commitSha) }}</template>
            </span>
          </div>
          <span class="space-info-badge" :class="'badge-' + activeSpace.status.toLowerCase()">{{ projectSpaceStore.statusLabel(activeSpace.status) }}</span>
          <ChevronDown class="space-info-chevron" aria-hidden="true" :size="15" :stroke-width="1.8" />
        </button>

        <div v-if="spaceInfoExpanded" class="space-info-detail">
          <p v-if="activeSpace.description" class="space-info-desc">{{ activeSpace.description }}</p>
          <div class="space-info-meta">
            <span>{{ projectSpaceStore.members.length }} 个仓库</span>
            <span v-if="activeSpace.lastIndexedAt">索引 {{ formatTime(activeSpace.lastIndexedAt) }}</span>
          </div>
          <div v-if="primaryMembers.length" class="commit-summary">
            <div class="commit-summary-title">
              <GitCommitHorizontal aria-hidden="true" :size="14" :stroke-width="1.8" />
              分支最后提交
            </div>
            <div v-for="member in primaryMembers" :key="member.id" class="commit-row">
              <div class="commit-main">
                <span class="commit-repo">{{ member.repositoryName }}</span>
                <span class="commit-branch">{{ member.branchName }}</span>
              </div>
              <div class="commit-detail">
                <code>{{ shortSha(member.commitSha) }}</code>
                <span>{{ member.commitMessage || '暂无 Commit Message' }}</span>
              </div>
              <time v-if="member.recentCommits?.[0]?.committedAt">{{ formatCommitTime(member.recentCommits[0].committedAt) }}</time>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="sidebar-history-section">
      <div class="sidebar-search">
        <Search class="sidebar-search-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        <input
          v-model="searchKeyword"
          class="sidebar-search-input"
          type="text"
          placeholder="搜索消息..."
        />
      </div>

      <section class="sidebar-panel">
        <div v-if="filteredGroups.length" class="history-list">
          <div
            v-for="group in visibleGroups"
            :key="group.conversationId"
            class="conversation-group"
          >
            <div
              class="conversation-card"
              :class="{ 'card-active': activeGroupId === group.conversationId }"
              @click="enterConversation(group.conversationId)"
            >
              <div class="card-main">
                <button
                  class="card-caret"
                  type="button"
                  @click.stop="toggleGroup(group.conversationId)"
                >
                  {{ isGroupExpanded(group.conversationId) ? '▾' : '▸' }}
                </button>
                <div class="card-body">
                  <p class="card-title">{{ group.title }}</p>
                  <div class="card-meta">
                    <span class="card-count">{{ group.questionCount }} 轮</span>
                    <span class="card-dot">·</span>
                    <span class="card-time">{{ formatTime(group.lastActiveAt) }}</span>
                  </div>
                </div>
                <button
                  class="card-delete"
                  type="button"
                  title="删除对话"
                  :disabled="deletingId === group.conversationId"
                  @click.stop="deleteConversation(group.conversationId)"
                >
                  <Trash2 aria-hidden="true" :size="14" :stroke-width="1.8" />
                </button>
              </div>
            </div>
            <div v-if="isGroupExpanded(group.conversationId)" class="group-questions">
              <ChatHistoryItem
                v-for="item in group.questions"
                :key="item.id"
                :question="item"
                :active="false"
                @click="selectQuestion(item.id)"
              />
            </div>
          </div>
          <button
            v-if="hasMoreGroups"
            class="load-more-button"
            type="button"
            @click="loadMore"
          >
            加载更多（剩余 {{ filteredGroups.length - visibleCount }} 条）
          </button>
        </div>
        <div v-else class="empty-box compact-empty">
          <p>还没有对话记录。</p>
          <span>点击上方新建对话开始。</span>
        </div>
      </section>
    </div>

    <div class="sidebar-footer">
      <button
        v-if="questionStore.activeConversationId"
        class="sidebar-export-button"
        type="button"
        @click="exportChat"
      >
        <Download class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        导出对话
      </button>

    </div>
  </aside>
</template>

<style scoped>
.sidebar-space-slot,
.sidebar-history-section,
.sidebar-footer {
  width: 100%;
}

.sidebar-space-slot {
  padding: 0 0 var(--spacing-2);
  min-height: 58px;
  border-bottom: 1px solid var(--chat-divider);
}

.sidebar-history-section {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: var(--spacing-2);
  min-height: 0;
  padding: var(--spacing-2) 0 0;
}

.sidebar-panel {
  min-height: 0;
  overflow: auto;
}

.sidebar-footer {
  display: grid;
  gap: var(--spacing-2);
  padding-top: var(--spacing-2);
  border-top: 1px solid var(--chat-divider);
}

.conversation-group {
  margin-bottom: var(--spacing-2);
  position: relative;
  z-index: 1;
}

.conversation-group:hover {
  z-index: 2;
}

.conversation-card {
  position: relative;
  padding: var(--spacing-3) var(--spacing-3);
  background: var(--surface);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
  cursor: pointer;
  transition:
    box-shadow var(--transition-normal),
    border-color var(--transition-normal),
    background var(--transition-normal);
}

.conversation-card:hover {
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
  border-color: var(--stroke-strong);
}

.conversation-card.card-active {
  border-color: var(--chat-accent);
  background: var(--chat-accent-soft);
  box-shadow: 0 2px 8px rgba(79, 110, 247, 0.1);
}

.conversation-card.card-active:hover {
  box-shadow: 0 4px 14px rgba(79, 110, 247, 0.15);
  z-index: 2;
}

.card-main {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-2);
}

.card-caret {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  padding: 0;
  border: none;
  border-radius: var(--radius-xs);
  background: none;
  color: var(--muted);
  font-size: var(--font-size-xs);
  cursor: pointer;
  transition:
    background var(--transition-fast),
    color var(--transition-fast);
}

.card-caret:hover {
  background: var(--primary-soft);
  color: var(--text);
}

.card-body {
  flex: 1;
  min-width: 0;
}

.card-title {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--text);
  line-height: var(--line-height-tight);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  word-break: break-all;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  margin-top: var(--spacing-1);
}

.card-count {
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  color: var(--chat-accent);
}

.card-dot {
  font-size: var(--font-size-xs);
  color: var(--muted);
}

.card-time {
  font-size: var(--font-size-xs);
  color: var(--chat-timestamp);
}

.card-delete {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-top: 1px;
  padding: 0;
  border: none;
  border-radius: var(--radius-sm);
  background: none;
  color: var(--muted);
  cursor: pointer;
  opacity: 0;
  transition:
    opacity var(--transition-fast),
    background var(--transition-fast),
    color var(--transition-fast);
}

.conversation-card:hover .card-delete {
  opacity: 1;
}

.card-delete:hover {
  background: rgba(220, 38, 38, 0.08);
  color: var(--danger);
}

.card-delete:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.group-questions {
  padding: var(--spacing-1) 0 0 var(--spacing-6);
}

.load-more-button {
  appearance: none;
  width: 100%;
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px dashed var(--stroke);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  cursor: pointer;
  text-align: center;
  transition:
    border-color var(--transition-fast),
    color var(--transition-fast),
    background var(--transition-fast);
}

.load-more-button:hover {
  border-color: var(--chat-accent);
  color: var(--chat-accent);
  background: var(--chat-accent-soft);
}

.sidebar-search {
  position: relative;
  padding: 0;
}

.sidebar-search-icon {
  position: absolute;
  left: var(--spacing-3);
  top: 50%;
  transform: translateY(-50%);
  color: var(--chat-timestamp);
  font-size: var(--font-size-sm);
  pointer-events: none;
}

.sidebar-search-input {
  width: 100%;
  padding: var(--spacing-2) var(--spacing-3) var(--spacing-2) 34px;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface-soft);
  color: var(--text);
  font-size: var(--font-size-sm);
  outline: none;
  transition:
    border-color var(--transition-normal),
    box-shadow var(--transition-normal),
    background var(--transition-normal);
}

.sidebar-search-input:focus {
  border-color: var(--chat-composer-focus);
  box-shadow: 0 0 0 3px rgba(79, 110, 247, 0.08);
  background: var(--surface);
}

.sidebar-search-input::placeholder {
  color: var(--muted);
}

.sidebar-export-button {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-full);
  background: var(--surface);
  padding: var(--spacing-2) var(--spacing-3);
  cursor: pointer;
  color: var(--subtle);
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-medium);
  text-align: center;
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast);
}

.sidebar-export-button:hover {
  border-color: var(--stroke-strong);
  background: var(--primary-soft);
}

.brand-block {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.space-info {
  display: grid;
  gap: var(--spacing-2);
  padding: var(--spacing-2);
  background: var(--surface-soft);
  border-radius: var(--radius-lg);
  border: none;
}

.back-space-button {
  appearance: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-1);
  width: 100%;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-full);
  background: var(--surface);
  padding: var(--spacing-2) var(--spacing-3);
  color: var(--subtle);
  cursor: pointer;
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  transition:
    background var(--transition-fast),
    border-color var(--transition-fast),
    color var(--transition-fast);
}

.back-space-button:hover {
  border-color: var(--chat-accent);
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
}

.space-info-summary {
  appearance: none;
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  width: 100%;
  min-width: 0;
  padding: var(--spacing-1) 0;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.space-info-summary:hover .space-info-name {
  color: var(--chat-accent);
}

.space-info-summary:focus-visible {
  border-radius: var(--radius-sm);
}

.space-info-header {
  display: flex;
  align-items: flex-start;
  gap: var(--spacing-2);
}

.space-info-icon {
  flex-shrink: 0;
  color: var(--chat-accent);
}

.space-info-title {
  flex: 1;
  display: grid;
  min-width: 0;
  gap: 2px;
}

.space-info-project {
  color: var(--chat-timestamp);
  font-size: 10px;
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.03em;
  text-transform: uppercase;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-info-name {
  font-size: var(--font-size-md);
  font-weight: var(--font-weight-semibold);
  color: var(--text);
  line-height: var(--line-height-tight);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: color var(--transition-fast);
}

.space-info-brief {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: var(--font-size-xs);
  line-height: var(--line-height-tight);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.space-info-badge {
  flex-shrink: 0;
  padding: 1px 6px;
  border-radius: var(--radius-full);
  font-size: 10px;
  font-weight: var(--font-weight-medium);
  line-height: 1.6;
}

.space-info-chevron {
  flex-shrink: 0;
  color: var(--chat-timestamp);
  transition: transform var(--transition-fast);
}

.space-info-expanded .space-info-chevron {
  transform: rotate(180deg);
}

.space-info-detail {
  display: grid;
  gap: var(--spacing-2);
  padding-top: var(--spacing-1);
}

.badge-ready {
  background: rgba(5, 150, 105, 0.1);
  color: var(--success);
}

.badge-indexing,
.badge-preparing {
  background: rgba(217, 119, 6, 0.1);
  color: var(--warning);
}

.badge-stale {
  background: rgba(217, 119, 6, 0.1);
  color: var(--warning);
}

.badge-failed {
  background: rgba(220, 38, 38, 0.08);
  color: var(--danger);
}

.badge-created,
.badge-ready_to_index {
  background: var(--primary-soft);
  color: var(--muted);
}

.space-info-desc {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--muted);
  line-height: var(--line-height-normal);
}

.space-info-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--spacing-2);
  font-size: var(--font-size-xs);
  color: var(--chat-timestamp);
}

.commit-summary {
  display: grid;
  gap: var(--spacing-2);
  padding-top: var(--spacing-2);
  border-top: 1px solid var(--chat-divider);
}

.commit-summary-title {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
  color: var(--chat-timestamp);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
}

.commit-row {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.commit-main,
.commit-detail {
  display: flex;
  align-items: center;
  gap: var(--spacing-1);
  min-width: 0;
}

.commit-repo {
  min-width: 0;
  overflow: hidden;
  color: var(--text);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.commit-branch {
  flex-shrink: 0;
  max-width: 86px;
  overflow: hidden;
  border-radius: var(--radius-full);
  background: var(--primary-soft);
  padding: 1px 6px;
  color: var(--muted);
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.commit-detail code {
  flex-shrink: 0;
  border-radius: var(--radius-xs);
  background: var(--surface);
  padding: 1px 4px;
  color: var(--chat-accent);
  font-size: 10px;
}

.commit-detail span {
  min-width: 0;
  overflow: hidden;
  color: var(--muted);
  font-size: var(--font-size-xs);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.commit-row time {
  color: var(--chat-timestamp);
  font-size: 10px;
}

.sidebar-footer {
  display: grid;
  gap: var(--spacing-2);
}

.mobile-close-btn {
  display: none;
  position: absolute;
  right: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  cursor: pointer;
  font-size: 16px;
  color: var(--muted);
  align-items: center;
  justify-content: center;
}

.mobile-close-btn:hover {
  background: var(--primary-soft);
}

/* 响应式：平板端 */
@media (max-width: 1024px) {
  .card-delete {
    opacity: 1;
  }
}

/* 响应式：移动端 */
@media (max-width: 600px) {
  .mobile-close-btn {
    display: flex;
  }

  .conversation-card {
    padding: var(--spacing-2) var(--spacing-3);
  }

  .card-title {
    font-size: var(--font-size-sm);
    -webkit-line-clamp: 2;
  }

  .card-delete {
    opacity: 1;
    width: 30px;
    height: 30px;
  }

  .card-caret {
    width: 22px;
    height: 22px;
  }
}
</style>
