<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted } from 'vue'
import { ArrowLeft, Code2 } from 'lucide-vue-next'
import { useRepositoryStore } from '../stores/repository'
import { useProjectStore } from '../stores/project'
import { useProjectSpaceStore } from '../stores/projectSpace'

const ProjectSection = defineAsyncComponent(() =>
  import('../components/settings/ProjectSection.vue')
)
const ProjectSpaceSection = defineAsyncComponent(() =>
  import('../components/settings/ProjectSpaceSection.vue')
)

const props = defineProps<{
  projectId?: string | number
  projectSpaceId?: string | number
}>()

const repositoryStore = useRepositoryStore()
const projectStore = useProjectStore()
const projectSpaceStore = useProjectSpaceStore()

const sourceSpaceId = computed(() => {
  const id = Number(props.projectSpaceId)
  return Number.isFinite(id) && id > 0 ? id : null
})

const targetProjectId = computed(() => {
  const id = Number(props.projectId)
  return Number.isFinite(id) && id > 0 ? id : null
})

const mode = computed(() => {
  if (sourceSpaceId.value != null) return 'derive'
  if (targetProjectId.value != null) return 'analysis-create'
  return 'create'
})

const activeStep = computed(() =>
  mode.value === 'analysis-create'
    ? 2
    : projectStore.projects.length && projectStore.members.length
      ? 2
      : projectStore.projects.length
        ? 1
        : repositoryStore.repositories.length
          ? 0
          : -1
)

const kicker = computed(() => {
  if (mode.value === 'derive') return '分析空间新版本'
  if (mode.value === 'analysis-create') return '创建分析空间'
  return '分析空间配置'
})

const title = computed(() => {
  if (mode.value === 'derive') return '基于当前空间创建新分析空间'
  if (mode.value === 'analysis-create') return '为当前项目空间创建可提问版本'
  return '创建一个可提问的代码分析空间'
})

const description = computed(() => {
  if (mode.value === 'derive') {
    return '当前系统会保留原分析空间，并用新的分支组合创建一个可提问的新空间。'
  }
  if (mode.value === 'analysis-create') {
    return '选择分支组合后，系统会自动准备代码并执行 CodeGraph 索引。完成后即可进入聊天提问。'
  }
  return '选择项目和仓库分支，系统会自动准备代码并执行 CodeGraph 索引。完成后即可进入聊天提问。'
})

const showProjectSetup = computed(() => mode.value === 'create')

const showFlowBar = computed(() => mode.value !== 'derive')

const currentProject = computed(() =>
  targetProjectId.value == null
    ? null
    : projectStore.projects.find((project) => project.id === targetProjectId.value) ?? null
)

const backTo = computed(() =>
  targetProjectId.value == null ? '/projects' : `/projects/${targetProjectId.value}`
)

const projectSummary = computed(() =>
  currentProject.value
    ? `${currentProject.value.name} 的仓库已经在项目空间中维护，这里只需要选择本次要分析的分支组合。`
    : '正在加载当前项目空间。'
)

onMounted(() => {
  projectStore.fetch()
  projectSpaceStore.fetch()
  repositoryStore.fetch()
})
</script>

<template>
  <section class="project-page project-config-page">
    <div class="project-page-header">
      <span class="icon-badge project-page-icon" aria-hidden="true">
        <Code2 :size="18" :stroke-width="1.8" />
      </span>
      <div>
        <p class="kicker">{{ kicker }}</p>
        <h1>{{ title }}</h1>
        <p class="project-page-copy">{{ description }}</p>
      </div>
      <router-link :to="backTo">
        <el-button>
          <ArrowLeft class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          {{ targetProjectId == null ? '返回首页' : '返回项目空间' }}
        </el-button>
      </router-link>
    </div>

    <section v-if="showFlowBar" class="config-flow-bar">
      <el-steps :active="activeStep" finish-status="success">
        <el-step title="选择项目" />
        <el-step title="确认仓库" />
        <el-step title="配置分支并索引" />
      </el-steps>
    </section>

    <section v-if="mode === 'analysis-create'" class="current-project-card">
      <div>
        <p class="kicker">当前项目空间</p>
        <h2>{{ currentProject?.name ?? '加载中' }}</h2>
        <p>{{ projectSummary }}</p>
      </div>
    </section>

    <ProjectSection v-if="showProjectSetup" />

    <ProjectSpaceSection
      :mode="mode === 'derive' ? 'derive' : 'create'"
      :source-space-id="sourceSpaceId"
      :project-id="targetProjectId"
    />
  </section>
</template>

<style scoped>
.config-flow-bar {
  border: 1px solid var(--stroke);
  border-radius: var(--radius-xl);
  background: var(--surface);
  padding: var(--spacing-5) var(--spacing-6);
  box-shadow: var(--shadow-soft);
}

.current-project-card {
  border: 1px solid var(--stroke);
  border-radius: var(--radius-xl);
  background: var(--surface);
  padding: var(--spacing-5) var(--spacing-6);
  box-shadow: var(--shadow-soft);
}

.current-project-card h2 {
  margin: 0;
  font-size: var(--font-size-xl);
}

.current-project-card p:last-child {
  margin: var(--spacing-2) 0 0;
  color: var(--text-muted);
}
</style>
