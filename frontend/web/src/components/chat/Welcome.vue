<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { useQuestionStore } from '../../stores/question'
import { useProjectSpaceStore } from '../../stores/projectSpace'

const router = useRouter()
const questionStore = useQuestionStore()
const projectSpaceStore = useProjectSpaceStore()

const selectedSpaceName = computed(() => {
  if (!questionStore.form.projectSpaceId) return null
  const space = projectSpaceStore.spaces.find((s) => s.id === questionStore.form.projectSpaceId)
  return space?.name ?? null
})

function goToProjects() {
  router.push('/projects')
}
</script>

<template>
  <div class="welcome-stage">
    <div class="welcome-copy-block">
      <h2 class="welcome-title">今天想了解哪段代码？</h2>
      <p v-if="!questionStore.form.projectSpaceId" class="welcome-copy">请从空间详情页进入聊天工作台。</p>
      <p v-else class="welcome-copy">项目空间：{{ selectedSpaceName }}，直接用自然语言提问。</p>
    </div>

    <el-button
      v-if="!questionStore.form.projectSpaceId"
      type="primary"
      title="返回项目空间"
      aria-label="返回项目空间"
      @click="goToProjects"
    >
      <ArrowLeft class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
      返回项目空间
    </el-button>


  </div>
</template>
