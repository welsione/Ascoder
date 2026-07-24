<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleX, RefreshCw } from 'lucide-vue-next'
import { useAsyncTaskStore } from '../../stores/asyncTask'
import type { TaskKind, TaskStatus } from '../../types/asyncTask'

const store = useAsyncTaskStore()

const kindOptions: { label: string; value: TaskKind }[] = [
  { label: 'Git 克隆', value: 'GIT_CLONE' },
  { label: 'Git 同步', value: 'GIT_FETCH' },
  { label: 'CodeGraph 索引', value: 'CODEGRAPH_INDEX' },
  { label: 'CodeGraph 同步', value: 'CODEGRAPH_SYNC' },
  { label: '项目空间准备', value: 'PROJECT_SPACE_PREPARE' },
  { label: '分支刷新', value: 'BRANCH_REFRESH' },
]

const statusOptions: { label: string; value: TaskStatus }[] = [
  { label: '排队中', value: 'QUEUED' },
  { label: '运行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCEEDED' },
  { label: '失败', value: 'FAILED' },
  { label: '已取消', value: 'CANCELLED' },
]

const kindLabels: Record<TaskKind, string> = {
  GIT_CLONE: 'Git 克隆',
  GIT_FETCH: 'Git 同步',
  CODEGRAPH_INDEX: 'CodeGraph 索引',
  CODEGRAPH_SYNC: 'CodeGraph 同步',
  PROJECT_SPACE_PREPARE: '项目空间准备',
  BRANCH_REFRESH: '分支刷新',
}

const statusConfig: Record<TaskStatus, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = {
  QUEUED: { label: '排队中', type: 'info' },
  RUNNING: { label: '运行中', type: 'warning' },
  SUCCEEDED: { label: '成功', type: 'success' },
  FAILED: { label: '失败', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' },
}

const kindTagType: Record<TaskKind, '' | 'success' | 'warning' | 'info' | 'danger'> = {
  GIT_CLONE: '',
  GIT_FETCH: 'success',
  CODEGRAPH_INDEX: 'warning',
  CODEGRAPH_SYNC: 'info',
  PROJECT_SPACE_PREPARE: 'danger',
  BRANCH_REFRESH: 'success',
}

const filterKind = ref<TaskKind | ''>('')
const filterStatus = ref<TaskStatus[]>([])

function onFilterKindChange() {
  store.setFilterKind(filterKind.value)
}

function onFilterStatusChange() {
  store.setFilterStatus(filterStatus.value)
}

function formatTime(value?: string | null) {
  if (!value) return '--'
  try {
    const date = new Date(value)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  } catch {
    return value
  }
}

function formatDuration(startedAt?: string | null, finishedAt?: string | null) {
  if (!startedAt) return '--'
  const start = new Date(startedAt).getTime()
  const end = finishedAt ? new Date(finishedAt).getTime() : Date.now()
  const diffMs = end - start
  if (diffMs < 0) return '--'
  const seconds = Math.floor(diffMs / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remainSeconds = seconds % 60
  if (minutes < 60) return `${minutes}m${remainSeconds}s`
  const hours = Math.floor(minutes / 60)
  const remainMinutes = minutes % 60
  return `${hours}h${remainMinutes}m`
}

function isCancellable(status: TaskStatus) {
  return status === 'QUEUED' || status === 'RUNNING'
}

async function handleCancel(taskId: number) {
  try {
    await ElMessageBox.confirm('确定要取消该任务吗？运行中的任务将被中断。', '取消任务', {
      confirmButtonText: '确定取消',
      cancelButtonText: '返回',
      type: 'warning',
    })
    await store.cancelTask(taskId)
    ElMessage.success('任务已取消')
  } catch {
    // 用户点击了返回，不做任何操作
  }
}

function rowClassName({ row }: { row: { status: TaskStatus } }) {
  if (row.status === 'RUNNING') return 'task-row-running'
  return ''
}

// 自动刷新：有运行中任务时每 10 秒刷新
let refreshTimer: ReturnType<typeof setInterval> | null = null

function startAutoRefresh() {
  stopAutoRefresh()
  refreshTimer = setInterval(() => {
    if (store.hasRunning) {
      store.refresh()
    } else {
      stopAutoRefresh()
    }
  }, 10_000)
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
}

onMounted(() => {
  store.fetchTasks()
  startAutoRefresh()
})

onUnmounted(() => {
  stopAutoRefresh()
})
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">任务管理</p>
        <h2>查看和管理所有异步任务的执行状态与进度</h2>
      </div>
      <div style="display:flex;gap:8px;">
        <el-button circle :loading="store.loading" title="刷新" @click="store.refresh">
          <RefreshCw :size="16" :stroke-width="1.8" />
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <div class="task-filter-bar">
      <el-select
        v-model="filterKind"
        placeholder="全部类型"
        clearable
        style="width:180px;"
        @change="onFilterKindChange"
      >
        <el-option
          v-for="opt in kindOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-select
        v-model="filterStatus"
        placeholder="全部状态"
        multiple
        clearable
        collapse-tags
        collapse-tags-tooltip
        style="width:240px;"
        @change="onFilterStatusChange"
      >
        <el-option
          v-for="opt in statusOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </div>

    <el-alert v-if="store.error" type="error" :title="store.error" show-icon :closable="false" style="margin-bottom:12px;" />

    <el-table
      v-loading="store.loading"
      :data="store.tasks"
      :row-class-name="rowClassName"
      empty-text="暂无任务"
      style="width:100%;"
    >
      <el-table-column label="类型" width="160">
        <template #default="{ row }">
          <el-tag :type="kindTagType[row.kind as TaskKind]" size="small">
            {{ kindLabels[row.kind as TaskKind] }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusConfig[row.status as TaskStatus].type" size="small">
            {{ statusConfig[row.status as TaskStatus].label }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="120">
        <template #default="{ row }">
          <template v-if="row.status === 'QUEUED'">
            <span class="task-progress-waiting">等待中</span>
          </template>
          <template v-else-if="row.progress >= 0">
            <el-progress
              :percentage="row.progress"
              :stroke-width="6"
              :status="row.status === 'SUCCEEDED' ? 'success' : row.status === 'FAILED' ? 'exception' : undefined"
            />
          </template>
          <template v-else>
            <span class="task-progress-unknown">--</span>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="状态消息" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.statusMessage">{{ row.statusMessage }}</span>
          <span v-else-if="row.errorMessage" class="task-error-text">{{ row.errorMessage }}</span>
          <span v-else class="task-progress-unknown">--</span>
        </template>
      </el-table-column>
      <el-table-column label="业务 ID" width="100">
        <template #default="{ row }">
          {{ row.businessId ?? '--' }}
        </template>
      </el-table-column>
      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.queuedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="耗时" width="100">
        <template #default="{ row }">
          {{ formatDuration(row.startedAt, row.finishedAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-tooltip v-if="isCancellable(row.status)" content="取消任务" placement="top" :show-after="300">
            <el-button
              size="small"
              circle
              type="danger"
              :loading="store.cancellingIds.has(row.id)"
              aria-label="取消任务"
              @click="handleCancel(row.id)"
            >
              <CircleX :size="15" :stroke-width="1.8" />
            </el-button>
          </el-tooltip>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="task-pagination">
      <el-pagination
        v-model:current-page="store.currentPage"
        v-model:page-size="store.pageSize"
        :total="store.totalElements"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="store.changePage"
        @size-change="store.changePageSize"
      />
    </div>
  </section>
</template>

<style scoped>
.task-filter-bar {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  margin-bottom: var(--spacing-4);
}

.task-progress-waiting {
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.task-progress-unknown {
  color: var(--muted);
}

.task-error-text {
  color: var(--danger);
  font-size: var(--font-size-xs);
}

.task-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--spacing-4);
}

:deep(.task-row-running) {
  background: color-mix(in srgb, var(--warning) 6%, transparent);
}
</style>
