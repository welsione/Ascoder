import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as api from '../services/runtimeSettingsApi'
import type { RuntimeSetting, RuntimeSettingCategory } from '../types/runtimeSetting'

export const useRuntimeSettingStore = defineStore('runtimeSetting', () => {
  const settings = ref<RuntimeSetting[]>([])
  const loading = ref(false)
  const saving = ref(false)
  const error = ref<string>('')

  async function fetchAll() {
    loading.value = true
    error.value = ''
    try {
      settings.value = await api.listAll()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载运行时设置失败'
    } finally {
      loading.value = false
    }
  }

  async function update(key: string, value: string) {
    saving.value = true
    error.value = ''
    try {
      const updated = await api.update(key, { value })
      const idx = settings.value.findIndex(s => s.key === key)
      if (idx >= 0) {
        settings.value[idx] = updated
      }
    } catch (e) {
      error.value = e instanceof Error ? e.message : '保存失败'
      throw e
    } finally {
      saving.value = false
    }
  }

  async function resetCategory(category: RuntimeSettingCategory) {
    saving.value = true
    error.value = ''
    try {
      await api.reset(category)
      await fetchAll()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '恢复默认失败'
      throw e
    } finally {
      saving.value = false
    }
  }

  function settingsOf(category: RuntimeSettingCategory) {
    return settings.value.filter(s => s.category === category)
  }

  return {
    settings,
    loading,
    saving,
    error,
    fetchAll,
    update,
    resetCategory,
    settingsOf,
  }
})