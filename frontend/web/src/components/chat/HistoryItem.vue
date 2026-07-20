<script setup lang="ts">
import type { QuestionRecord } from '../../types/question'
import { questionPreview } from '../../utils/format'
import QuestionStatusTag from './StatusTag.vue'

defineProps<{
  question: QuestionRecord
  active: boolean
}>()

defineEmits<{
  click: []
}>()
</script>

<template>
  <button
    class="history-item"
    :class="{ active }"
    type="button"
    @click="$emit('click')"
  >
    <div class="history-item-head">
      <span class="history-item-repo">{{ question.projectSpaceName || question.repositoryName || '未命名' }}</span>
      <QuestionStatusTag :status="question.status" />
    </div>
    <p class="history-item-title">{{ questionPreview(question.conversationTitle || question.text) }}</p>
  </button>
</template>
