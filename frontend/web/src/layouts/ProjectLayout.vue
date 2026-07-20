<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import { useAppStore } from '../stores/app'
import { useProjectStore } from '../stores/project'
import { useProjectSpaceStore } from '../stores/projectSpace'
import { useQuestionStore } from '../stores/question'
import { useRepositoryStore } from '../stores/repository'

const questionStore = useQuestionStore()

onMounted(async () => {
  await Promise.all([
    useAppStore().check(),
    useRepositoryStore().fetch(),
    useProjectStore().fetch(),
    useProjectSpaceStore().fetch(),
    questionStore.fetch(),
  ])
})

onUnmounted(() => {
  // 不再需要轮询
})
</script>

<template>
  <section class="project-layout">
    <router-view />
  </section>
</template>
