import { ref, watch, onUnmounted, type Ref } from 'vue'

/**
 * 草稿自动保存 composable，定时将表单数据持久化到 localStorage，
 * 页面刷新或意外关闭后可恢复。
 *
 * hasDraft 仅在 localStorage 中存在历史草稿时为 true，
 * 用户手动编辑表单后草稿会自动保存，但不会因此显示"恢复草稿"按钮
 * （因为当前表单内容就是最新状态，无需恢复）。
 */
export function useDraftAutoSave<T extends Record<string, unknown>>(
  key: string,
  source: Ref<T>,
  intervalMs = 5000
) {
  const draftKey = `draft:${key}`
  const lastSaved = ref<string>('')
  const hasDraft = ref(false)

  /** 初始化时检查 localStorage 是否已有历史草稿 */
  function checkExistingDraft() {
    try {
      const raw = localStorage.getItem(draftKey)
      if (raw) {
        const current = JSON.stringify(source.value)
        // 只有草稿与当前表单不同时才标记为可恢复
        hasDraft.value = raw !== current
        lastSaved.value = raw
      }
    } catch {
      // localStorage 不可用时静默忽略
    }
  }

  function save() {
    try {
      const json = JSON.stringify(source.value)
      if (json !== lastSaved.value) {
        localStorage.setItem(draftKey, json)
        lastSaved.value = json
      }
    } catch {
      // localStorage 满或不可用时静默忽略
    }
  }

  function restore(): T | null {
    try {
      const raw = localStorage.getItem(draftKey)
      if (!raw) return null
      hasDraft.value = false
      return JSON.parse(raw) as T
    } catch {
      return null
    }
  }

  function clear() {
    localStorage.removeItem(draftKey)
    lastSaved.value = ''
    hasDraft.value = false
  }

  const timer = setInterval(save, intervalMs)

  // 保存后重新检查草稿状态（草稿与当前表单不同时 hasDraft=true）
  const stopWatch = watch(
    () => source.value,
    () => {
      try {
        const raw = localStorage.getItem(draftKey)
        if (raw) {
          hasDraft.value = raw !== JSON.stringify(source.value)
        }
      } catch {
        // 忽略
      }
    },
    { deep: true }
  )

  // 初始化时检查已有草稿
  checkExistingDraft()

  onUnmounted(() => {
    clearInterval(timer)
    stopWatch()
  })

  return { save, restore, clear, hasDraft }
}
