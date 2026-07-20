import { ref, watch, onUnmounted, type Ref } from 'vue'

/**
 * 草稿自动保存 composable，定时将表单数据持久化到 localStorage，
 * 页面刷新或意外关闭后可恢复。
 */
export function useDraftAutoSave<T extends Record<string, unknown>>(
  key: string,
  source: Ref<T>,
  intervalMs = 5000
) {
  const draftKey = `draft:${key}`
  const lastSaved = ref<string>('')
  const hasDraft = ref(false)

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
      hasDraft.value = true
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

  const stopWatch = watch(
    () => source.value,
    () => {
      hasDraft.value = true
    },
    { deep: true }
  )

  onUnmounted(() => {
    clearInterval(timer)
    stopWatch()
  })

  return { save, restore, clear, hasDraft }
}
