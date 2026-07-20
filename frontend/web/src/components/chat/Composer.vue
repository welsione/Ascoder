<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Clipboard, Eraser, Paperclip, Send } from 'lucide-vue-next'
import { useQuestionStore } from '../../stores/question'
import { useLlmProviderStore } from '../../stores/llmProvider'
import { roleOptions } from '../../constants'

const questionStore = useQuestionStore()
const llmProviderStore = useLlmProviderStore()
const input = ref('')
const textareaRef = ref<HTMLTextAreaElement | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

const DRAFT_KEY = 'ascoder:composer-draft'
const ROLE_KEY = 'ascoder:composer-role'
const CONTEXT_MENU_KEY = 'ascoder:context-menu'

const ALLOWED_LOG_EXT = ['.log', '.txt', '.zip']
const MAX_LOG_SIZE = 50 * 1024 * 1024 // 50MB

const currentRoleDescription = computed(
  () => roleOptions.find((opt) => opt.value === questionStore.form.role)?.description ?? ''
)

/** 消息状态文本 */
const statusText = ref('')
const statusType = ref<'idle' | 'sending' | 'sent' | 'failed'>('idle')
const composerDisabled = computed(() =>
  !questionStore.form.projectSpaceId
)
const placeholder = computed(() =>
  questionStore.form.projectSpaceId ? '输入你的问题… (Enter 发送，Shift+Enter 换行)' : '请先选择项目空间再提问'
)

/** 从 localStorage 恢复草稿 */
function restoreDraft() {
  try {
    const saved = localStorage.getItem(DRAFT_KEY)
    if (saved) {
      input.value = saved
      nextTick(() => adjustHeight())
    }
  } catch {
    // 忽略
  }
}

/** 保存草稿到 localStorage */
function saveDraft() {
  try {
    if (input.value.trim()) {
      localStorage.setItem(DRAFT_KEY, input.value)
    } else {
      localStorage.removeItem(DRAFT_KEY)
    }
  } catch {
    // 忽略
  }
}

let draftTimer: ReturnType<typeof setInterval> | null = null
onMounted(() => {
  restoreDraft()
  restoreRole()
  draftTimer = setInterval(saveDraft, 2000)
})

onUnmounted(() => {
  saveDraft()
  if (draftTimer) clearInterval(draftTimer)
})

/** 从 localStorage 恢复用户上次选择的角色 */
function restoreRole() {
  try {
    const saved = localStorage.getItem(ROLE_KEY)
    if (saved && roleOptions.some((opt) => opt.value === saved)) {
      questionStore.form.role = saved
    }
  } catch {
    // 忽略
  }
}

function onRoleChange(value: string) {
  try {
    localStorage.setItem(ROLE_KEY, value)
  } catch {
    // 忽略
  }
}

/** 自适应高度 */
function adjustHeight() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  const maxH = 200
  el.style.height = Math.min(el.scrollHeight, maxH) + 'px'
}

watch(input, () => {
  nextTick(adjustHeight)
  saveDraft()
})

async function submit() {
  const question = input.value.trim()
  if (!question || composerDisabled.value) return

  if (!llmProviderStore.hasProviders) {
    await llmProviderStore.fetchProviders()
    if (!llmProviderStore.hasProviders) {
      ElMessage.warning('尚未配置 LLM 供应商，请先在设置页配置')
      return
    }
  }

  questionStore.form.text = question
  input.value = ''
  localStorage.removeItem(DRAFT_KEY)
  nextTick(() => {
    if (textareaRef.value) {
      textareaRef.value.style.height = 'auto'
    }
  })

  statusType.value = 'sending'
  statusText.value = '发送中...'
  const submittedQuestionId = questionStore.submitStream()
  if (submittedQuestionId == null) {
    statusType.value = 'idle'
    statusText.value = ''
    return
  }

  // 只监听本次提交对应的流式状态，避免被其他运行中的问题影响。
  const unwatch = watch(
    () => questionStore.liveStateFor(submittedQuestionId)?.streaming ?? false,
    (streaming) => {
      if (!streaming) {
        if (questionStore.error) {
          statusType.value = 'failed'
          statusText.value = '发送失败'
        } else {
          statusType.value = 'sent'
          statusText.value = '已送达'
        }
        // 3秒后清除状态
        setTimeout(() => {
          statusType.value = 'idle'
          statusText.value = ''
        }, 3000)
        unwatch()
      }
    }
  )
}

function cancel() {
  const questionId = questionStore.activeStreamQuestionId
  if (questionId != null) {
    questionStore.cancelStreaming(true, questionId)
  }
}

function retry() {
  const retrying = questionStore.retryLastFailed()
  if (retrying) {
    statusType.value = 'sending'
    statusText.value = '重试中...'
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    submit()
  }
}

/** 右键/长按上下文菜单 */
const contextMenu = ref<{ visible: boolean; x: number; y: number }>({
  visible: false,
  x: 0,
  y: 0,
})

function onContextMenu(e: MouseEvent) {
  e.preventDefault()
  contextMenu.value = { visible: true, x: e.clientX, y: e.clientY }
}

function closeContextMenu() {
  contextMenu.value.visible = false
}

function pasteFromClipboard() {
  navigator.clipboard.readText().then((text) => {
    input.value += text
    nextTick(adjustHeight)
  }).catch(() => {})
  closeContextMenu()
}

function clearInput() {
  input.value = ''
  localStorage.removeItem(DRAFT_KEY)
  nextTick(() => {
    if (textareaRef.value) textareaRef.value.style.height = 'auto'
  })
  closeContextMenu()
}

function onDocumentClick() {
  if (contextMenu.value.visible) closeContextMenu()
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onUnmounted(() => document.removeEventListener('click', onDocumentClick))

/** 触发隐藏的文件 input */
function pickLogFile() {
  if (composerDisabled.value || questionStore.uploadingLog) return
  fileInputRef.value?.click()
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

async function onLogFileChange(e: Event) {
  const target = e.target as HTMLInputElement
  const files = target.files
  if (!files || files.length === 0) return
  if (!questionStore.form.projectSpaceId) {
    ElMessage.warning('请先选择项目空间')
    target.value = ''
    return
  }
  for (const file of Array.from(files)) {
    const lower = file.name.toLowerCase()
    if (!ALLOWED_LOG_EXT.some((ext) => lower.endsWith(ext))) {
      ElMessage.warning(`仅支持 .log / .txt / .zip 日志文件，已跳过：${file.name}`)
      continue
    }
    if (file.size > MAX_LOG_SIZE) {
      ElMessage.warning(`单个日志文件不能超过 50MB，已跳过：${file.name}`)
      continue
    }
    const result = await questionStore.attachLog(file)
    if (result) {
      ElMessage.success(`已附加日志：${result.originalFilename}`)
    } else if (questionStore.error) {
      ElMessage.error(questionStore.error)
    }
  }
  target.value = ''
}

function removeLogUpload(uploadId: number) {
  questionStore.removeLogUpload(uploadId)
}
</script>

<template>
  <div class="chat-composer">
    <div class="composer-panel">
      <div class="composer-toolbar">
        <div class="composer-role-group">
          <span class="composer-toolbar-label">回答视角</span>
          <el-select
            v-model="questionStore.form.role"
            size="small"
            class="composer-role-select"
            :disabled="composerDisabled"
            @change="onRoleChange"
          >
            <el-option
              v-for="opt in roleOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            >
              <span class="role-option-label">{{ opt.label }}</span>
              <span class="role-option-desc">{{ opt.description }}</span>
            </el-option>
          </el-select>
        </div>
        <span class="composer-role-hint">{{ currentRoleDescription }}</span>
        <span class="composer-shortcut">Enter 发送 / Shift+Enter 换行</span>
      </div>

      <div class="composer-input-wrap">
        <textarea
          ref="textareaRef"
          v-model="input"
          class="composer-textarea"
          :rows="1"
          :placeholder="placeholder"
          :disabled="composerDisabled"
          @keydown="handleKeydown"
          @contextmenu="onContextMenu"
        />
        <div class="composer-actions">
          <input
            ref="fileInputRef"
            type="file"
            accept=".log,.txt,.zip"
            multiple
            class="composer-file-input"
            @change="onLogFileChange"
          />
          <el-button
            size="small"
            :disabled="composerDisabled || questionStore.uploadingLog"
            :loading="questionStore.uploadingLog"
            class="composer-attach-btn"
            @click="pickLogFile"
          >
            <Paperclip class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
            日志
          </el-button>
          <el-button
            v-if="questionStore.activeStreaming"
            type="danger"
            size="small"
            class="composer-send-btn"
            @click="cancel"
          >
            停止
          </el-button>
          <el-button
            type="primary"
            class="composer-send-btn"
            :disabled="!input.trim() || composerDisabled"
            @click="submit"
          >
            <Send class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
            发送
          </el-button>
        </div>
      </div>

      <div
        v-if="questionStore.currentLogUploads.length"
        class="log-upload-chips"
      >
        <div
          v-for="upload in questionStore.currentLogUploads"
          :key="upload.id"
          class="log-upload-chip"
        >
          <span class="log-upload-icon">📄</span>
          <span class="log-upload-name">{{ upload.originalFilename }}</span>
          <span class="log-upload-size">{{ formatBytes(upload.fileSize) }}</span>
          <span
            v-if="upload.status"
            class="log-upload-status"
            :class="`status-${upload.status.toLowerCase()}`"
          >
            {{ upload.status }}
          </span>
          <button type="button" class="log-upload-remove" title="移除" @click="removeLogUpload(upload.id)">×</button>
        </div>
      </div>

      <!-- 状态栏 -->
      <div class="composer-status-bar">
        <Transition name="fade">
          <span
            v-if="statusType === 'failed'"
            class="status-badge status-failed"
          >
            <span class="status-dot" />
            {{ statusText }}
            <button type="button" class="retry-link" @click="retry">重试</button>
          </span>
          <span
            v-else-if="statusType === 'sent'"
            class="status-badge status-sent"
          >
            <span class="status-dot" />
            {{ statusText }}
          </span>
          <span
            v-else-if="statusType === 'sending'"
            class="status-badge status-sending"
          >
            <span class="status-dot" />
            {{ statusText }}
          </span>
        </Transition>

        <span v-if="questionStore.activeStreaming" class="streaming-hint">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>Agent 正在思考中...</span>
        </span>
      </div>
    </div>

    <!-- 右键/长按菜单 -->
    <Teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="context-menu"
        :style="{ left: contextMenu.x + 'px', top: contextMenu.y + 'px' }"
      >
        <button type="button" title="粘贴" aria-label="粘贴" @click="pasteFromClipboard">
          <Clipboard aria-hidden="true" :size="15" :stroke-width="1.8" />
          粘贴
        </button>
        <button v-if="input" type="button" title="清空" aria-label="清空" @click="clearInput">
          <Eraser aria-hidden="true" :size="15" :stroke-width="1.8" />
          清空
        </button>
      </div>
    </Teleport>
  </div>
</template>

<script lang="ts">
import { Loading } from '@element-plus/icons-vue'
export default { components: { Loading } }
</script>

<style scoped>
.chat-composer {
  padding: var(--spacing-4) 0 var(--spacing-5);
  background:
    linear-gradient(180deg, transparent, rgba(245, 245, 247, 0.82) 18%, var(--bg) 100%);
}

.composer-panel {
  display: grid;
  gap: var(--spacing-3);
  padding: var(--spacing-4);
  border: 1px solid rgba(79, 110, 247, 0.12);
  border-radius: var(--radius-2xl);
  background:
    radial-gradient(circle at 8% 0%, rgba(79, 110, 247, 0.08), transparent 34%),
    var(--chat-composer-bg);
  box-shadow:
    0 18px 46px rgba(15, 23, 42, 0.08),
    0 1px 2px rgba(15, 23, 42, 0.04);
  transition:
    border-color var(--transition-normal),
    box-shadow var(--transition-normal),
    transform var(--transition-normal);
}

.composer-panel:focus-within {
  border-color: rgba(79, 110, 247, 0.34);
  box-shadow:
    0 0 0 3px rgba(79, 110, 247, 0.1),
    0 22px 56px rgba(15, 23, 42, 0.1),
    0 1px 2px rgba(15, 23, 42, 0.05);
}

.composer-toolbar {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  font-size: var(--font-size-xs);
  min-width: 0;
}

.composer-toolbar-label {
  color: var(--muted);
  font-weight: var(--font-weight-semibold);
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.composer-role-group {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  flex: 0 0 auto;
}

.composer-role-select {
  width: 128px;
}

.composer-role-hint {
  color: var(--muted);
  font-size: var(--font-size-xs);
  flex: 1;
  min-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer-shortcut {
  flex: 0 0 auto;
  color: var(--chat-timestamp);
  font-size: var(--font-size-xs);
}

.role-option-label {
  font-weight: var(--font-weight-medium);
  margin-right: var(--spacing-2);
}

.role-option-desc {
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.composer-input-wrap {
  display: flex;
  align-items: stretch;
  gap: var(--spacing-3);
  padding: var(--spacing-2);
  border: 1px solid var(--chat-composer-border);
  border-radius: var(--radius-xl);
  background: var(--surface-soft);
  box-shadow: var(--shadow-inset);
  transition:
    border-color var(--transition-normal),
    box-shadow var(--transition-normal),
    background var(--transition-normal);
}

.composer-input-wrap:focus-within {
  border-color: var(--chat-composer-focus);
  background: var(--surface);
  box-shadow:
    0 0 0 3px rgba(79, 110, 247, 0.09),
    var(--shadow-inset);
}

.composer-textarea {
  flex: 1;
  resize: none;
  padding: var(--spacing-3) var(--spacing-3);
  border: 0;
  border-radius: var(--radius-lg);
  background: transparent;
  color: var(--text);
  font-family: inherit;
  font-size: var(--font-size-lg);
  line-height: var(--line-height-relaxed);
  overflow-y: auto;
  min-height: 44px;
  max-height: 200px;
  transition:
    border-color var(--transition-normal),
    background var(--transition-normal);
  outline: none;
  scrollbar-width: thin;
  scrollbar-color: var(--scrollbar-thumb) var(--scrollbar-track);
}

.composer-textarea:focus {
  background: transparent;
}

.composer-textarea:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.composer-textarea::placeholder {
  color: var(--muted);
  font-size: var(--font-size-md);
}

.composer-textarea::-webkit-scrollbar {
  width: var(--scrollbar-size);
}

.composer-textarea::-webkit-scrollbar-thumb {
  background: var(--scrollbar-thumb);
  border-radius: var(--radius-full);
}

.composer-actions {
  display: flex;
  align-items: flex-end;
  gap: var(--spacing-2);
  flex-shrink: 0;
}

.composer-actions :deep(.el-button) {
  min-width: auto;
  height: 40px;
  margin-left: 0;
  padding: 0 var(--spacing-4);
  border-radius: var(--radius-full);
  font-weight: var(--font-weight-semibold);
}

.composer-attach-btn {
  border: 1px solid var(--stroke) !important;
  background: var(--surface) !important;
  color: var(--subtle) !important;
}

.composer-attach-btn:hover {
  border-color: rgba(79, 110, 247, 0.24) !important;
  background: var(--chat-accent-soft) !important;
  color: var(--chat-accent) !important;
}

.composer-send-btn {
  min-width: 96px !important;
  box-shadow: 0 10px 24px rgba(79, 110, 247, 0.22) !important;
}

.composer-send-btn.is-disabled,
.composer-send-btn.is-loading {
  box-shadow: none !important;
}

.composer-status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 24px;
  margin-top: var(--spacing-1);
}

.status-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-sending .status-dot {
  background: var(--warning);
  animation: pulse 1.2s infinite;
}

.status-sent .status-dot {
  background: var(--success);
}

.status-failed .status-dot {
  background: var(--danger);
}

.retry-link {
  padding: 0;
  border: none;
  background: none;
  color: var(--chat-accent);
  font-size: var(--font-size-xs);
  cursor: pointer;
  text-decoration: underline;
  font-weight: var(--font-weight-medium);
}

.retry-link:hover {
  opacity: 0.8;
}

.streaming-hint {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.streaming-hint .el-icon {
  color: var(--chat-accent);
}

/* ── 右键菜单 ── */

.context-menu {
  position: fixed;
  z-index: 9999;
  display: grid;
  gap: var(--spacing-1);
  padding: var(--spacing-1);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-md);
  background: var(--surface);
  box-shadow: var(--shadow-elevated);
  min-width: 120px;
}

.context-menu button {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  border: none;
  border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  font-size: var(--font-size-sm);
  cursor: pointer;
  text-align: left;
  transition: background var(--transition-fast);
}

.context-menu button:hover {
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.4; }
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.composer-file-input {
  display: none;
}

.composer-attach-btn {
  font-size: var(--font-size-xs);
}

.log-upload-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--spacing-2);
  justify-self: start;
}

.log-upload-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  padding: var(--spacing-2) var(--spacing-3);
  border: 1px solid rgba(79, 110, 247, 0.14);
  border-radius: var(--radius-full);
  background: var(--chat-accent-soft);
  font-size: var(--font-size-xs);
  max-width: 100%;
}

.log-upload-icon {
  flex-shrink: 0;
}

.log-upload-name {
  font-weight: var(--font-weight-medium);
  color: var(--text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 240px;
}

.log-upload-size {
  color: var(--muted);
}

.log-upload-status {
  padding: 0 var(--spacing-1);
  border-radius: var(--radius-sm);
  background: var(--chat-accent-soft);
  color: var(--chat-accent);
  font-size: 10px;
  text-transform: uppercase;
}

.log-upload-status.status-failed {
  background: rgba(239, 68, 68, 0.1);
  color: var(--danger);
}

.log-upload-status.status-running,
.log-upload-status.status-pending {
  background: rgba(234, 179, 8, 0.1);
  color: var(--warning);
}

.log-upload-remove {
  margin-left: var(--spacing-1);
  border: none;
  background: transparent;
  color: var(--muted);
  cursor: pointer;
  font-size: var(--font-size-md);
  line-height: 1;
  padding: 0 var(--spacing-1);
}

.log-upload-remove:hover {
  color: var(--danger);
}

:global([data-theme="dark"]) .chat-composer {
  background:
    linear-gradient(180deg, transparent, rgba(15, 15, 18, 0.82) 18%, var(--bg) 100%);
}

@media (prefers-color-scheme: dark) {
  :global(:root:not([data-theme="light"])) .chat-composer {
    background:
      linear-gradient(180deg, transparent, rgba(15, 15, 18, 0.82) 18%, var(--bg) 100%);
  }
}

@media (max-width: 760px) {
  .chat-composer {
    padding: var(--spacing-3) 0 0;
  }

  .composer-panel {
    border-radius: var(--radius-xl) var(--radius-xl) 0 0;
    padding: var(--spacing-3);
  }

  .composer-toolbar {
    align-items: flex-start;
  }

  .composer-role-hint {
    order: 3;
    flex-basis: 100%;
  }

  .composer-shortcut {
    display: none;
  }

  .composer-input-wrap {
    flex-direction: column;
  }

  .composer-actions {
    justify-content: flex-end;
  }
}
</style>
