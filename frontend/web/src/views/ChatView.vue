<script setup lang="ts">
import { computed, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useQuestionStore } from '../stores/question'
import { useProjectSpaceStore } from '../stores/projectSpace'
import ChatWelcome from '../components/chat/Welcome.vue'
import ChatThread from '../components/chat/Thread.vue'
import ChatComposer from '../components/chat/Composer.vue'

const route = useRoute()
const router = useRouter()
const questionStore = useQuestionStore()
const projectSpaceStore = useProjectSpaceStore()

const routeSpaceId = computed(() => {
  const value = route.query.spaceId
  const raw = Array.isArray(value) ? value[0] : value
  const id = raw ? Number(raw) : null
  return id && !Number.isNaN(id) ? id : null
})

const routeQuestionId = computed(() => {
  const queryValue = route.query.questionId
  const queryRaw = Array.isArray(queryValue) ? queryValue[0] : queryValue
  const paramRaw = Array.isArray(route.params.questionId)
    ? route.params.questionId[0]
    : route.params.questionId
  const raw = queryRaw ?? paramRaw
  const id = raw ? Number(raw) : null
  return id && Number.isFinite(id) ? id : null
})

watch(
  routeSpaceId,
  async (spaceId) => {
    if (!spaceId) {
      questionStore.form.projectSpaceId = null
      await router.replace('/projects')
      return
    }
    questionStore.form.projectSpaceId = spaceId
    projectSpaceStore.selectedSpaceId = spaceId
    await projectSpaceStore.fetchMembers(spaceId)
  },
  { immediate: true }
)

watch(
  routeQuestionId,
  async (questionId) => {
    if (!questionId) return
    const question = await questionStore.openQuestionThread(questionId)
    if (question?.status === 'RUNNING') {
      questionStore.resumeQuestion(questionId)
    }
  },
  { immediate: true }
)

watch(
  () => questionStore.activeQuestion?.id,
  (questionId) => {
    if (route.name !== 'chat' || !questionId) return
    if (routeQuestionId.value === questionId) return
    router.replace({
      name: 'chat',
      query: {
        ...route.query,
        questionId: String(questionId),
      },
    })
  }
)

const showThread = computed(() =>
  questionStore.activeConversationQuestions.length > 0
  || !!questionStore.activeQuestion
  || questionStore.activeStreaming
)

onUnmounted(() => {
  questionStore.cleanup()
})
</script>

<template>
  <section class="chat-stage">
    <ChatWelcome v-if="!showThread" class="chat-content" />
    <ChatThread v-else class="chat-thread" />

    <ChatComposer />
  </section>
</template>

<style scoped>
.chat-thread {
  min-height: 0;
  overflow: hidden;
}
</style>
