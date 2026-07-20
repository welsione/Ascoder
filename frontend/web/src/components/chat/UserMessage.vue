<script setup lang="ts">
import { computed } from 'vue'
import { Paperclip } from 'lucide-vue-next'
import type { QuestionRecord } from '../../types/question'
import { detailTime } from '../../utils/format'
import { roleOptions } from '../../constants'

const props = defineProps<{
  question: QuestionRecord
}>()

const roleKey = computed(() => props.question.role ?? 'developer')
const roleLabel = computed(
  () => roleOptions.find((opt) => opt.value === roleKey.value)?.label ?? roleKey.value
)
const roleTagType = computed<'primary' | 'warning' | 'success' | 'info'>(() => {
  switch (roleKey.value) {
    case 'developer':
      return 'primary'
    case 'product_manager':
      return 'warning'
    case 'tester':
      return 'success'
    default:
      return 'info'
  }
})

function formatFileSize(size: number) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function uploadTitle(upload: QuestionRecord['logUploads'][number]) {
  if (!upload.fileNames.length) return upload.originalFilename
  return upload.fileNames.join('、')
}
</script>

<template>
  <article class="message-bubble message-user">
    <div class="message-head">
      <p class="bubble-label">你的问题</p>
      <el-tag size="small" effect="plain">{{ question.repositoryName || question.projectSpaceName || '' }}</el-tag>
    </div>
    <h3>{{ question.text }}</h3>
    <div v-if="question.logUploads?.length" class="question-attachments" aria-label="上传的文件">
      <div
        v-for="upload in question.logUploads"
        :key="upload.id"
        class="question-attachment"
        :title="uploadTitle(upload)"
      >
        <Paperclip aria-hidden="true" :size="14" :stroke-width="1.8" />
        <span class="attachment-name">{{ upload.originalFilename }}</span>
        <span class="attachment-size">{{ formatFileSize(upload.fileSize) }}</span>
      </div>
    </div>
    <div class="bubble-meta">
      <el-tag size="small" :type="roleTagType" effect="light" class="role-tag">{{ roleLabel }}</el-tag>
      <span>{{ detailTime(question) }}</span>
    </div>
  </article>
</template>

<style scoped>
.role-tag {
  margin-right: var(--spacing-1);
}

.question-attachments {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
}

.question-attachment {
  display: inline-flex;
  align-items: center;
  max-width: 100%;
  gap: var(--spacing-1);
  padding: 4px var(--spacing-2);
  color: var(--text);
  font-size: var(--font-size-xs);
  background: rgba(255, 255, 255, 0.68);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
}

.attachment-name {
  min-width: 0;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-size {
  color: var(--muted);
  white-space: nowrap;
}
</style>
