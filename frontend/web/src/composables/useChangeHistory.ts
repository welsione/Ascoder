import { ref, type Ref } from 'vue'

interface ChangeRecord {
  field: string
  oldValue: unknown
  newValue: unknown
  timestamp: number
}

/**
 * 变更历史 composable，记录表单字段变更，
 * 支持回溯到任意历史快照。
 */
export function useChangeHistory<T extends Record<string, unknown>>(
  source: Ref<T>,
  maxRecords = 50
) {
  const history = ref<ChangeRecord[]>([])
  const snapshots = ref<Array<{ data: T; timestamp: number }>>([])

  function trackChange(field: string, oldValue: unknown, newValue: unknown) {
    if (oldValue === newValue) return
    history.value.push({ field, oldValue, newValue, timestamp: Date.now() })
    if (history.value.length > maxRecords) {
      history.value = history.value.slice(-maxRecords)
    }
  }

  function takeSnapshot() {
    snapshots.value.push({
      data: JSON.parse(JSON.stringify(source.value)),
      timestamp: Date.now(),
    })
    if (snapshots.value.length > maxRecords) {
      snapshots.value = snapshots.value.slice(-maxRecords)
    }
  }

  function restoreSnapshot(index: number): T | null {
    const snapshot = snapshots.value[index]
    if (!snapshot) return null
    return JSON.parse(JSON.stringify(snapshot.data)) as T
  }

  function clearHistory() {
    history.value = []
    snapshots.value = []
  }

  const hasHistory = () => history.value.length > 0
  const hasSnapshots = () => snapshots.value.length > 0

  return {
    history,
    snapshots,
    trackChange,
    takeSnapshot,
    restoreSnapshot,
    clearHistory,
    hasHistory,
    hasSnapshots,
  }
}
