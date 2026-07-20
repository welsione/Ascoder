import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import * as api from '../services/llmProviderApi'
import type { LlmProvider, CreateLlmProviderRequest, ConnectionTestResponse } from '../types/llmProvider'

export const useLlmProviderStore = defineStore('llmProvider', () => {
  const providers = ref<LlmProvider[]>([])
  const loading = ref(false)
  const error = ref('')
  const testResult = ref<ConnectionTestResponse | null>(null)

  const hasProviders = computed(() => providers.value.length > 0)
  const defaultProvider = computed(() => providers.value.find(p => p.isDefault) ?? null)
  const enabledProviders = computed(() => providers.value.filter(p => p.enabled))

  async function fetchProviders() {
    loading.value = true
    error.value = ''
    try {
      providers.value = await api.list()
    } catch {
      error.value = '无法加载 LLM 供应商'
    } finally {
      loading.value = false
    }
  }

  async function createProvider(payload: CreateLlmProviderRequest) {
    error.value = ''
    try {
      const created = await api.create(payload)
      await fetchProviders()
      return created
    } catch {
      error.value = '无法创建 LLM 供应商'
      return null
    }
  }

  async function updateProvider(id: number, payload: CreateLlmProviderRequest) {
    error.value = ''
    try {
      const updated = await api.update(id, payload)
      await fetchProviders()
      return updated
    } catch {
      error.value = '无法更新 LLM 供应商'
      return null
    }
  }

  async function deleteProvider(id: number) {
    error.value = ''
    try {
      await api.remove(id)
      await fetchProviders()
    } catch {
      error.value = '无法删除 LLM 供应商'
    }
  }

  async function testConnection(id: number) {
    testResult.value = null
    try {
      testResult.value = await api.testConnection(id)
    } catch {
      testResult.value = { success: false, message: '连接测试请求失败', latencyMs: null }
    }
  }

  async function setDefault(id: number) {
    error.value = ''
    try {
      await api.setDefault(id)
      await fetchProviders()
    } catch {
      error.value = '无法设置默认供应商'
    }
  }

  async function updateEnabled(id: number, enabled: boolean) {
    error.value = ''
    try {
      await api.updateEnabled(id, enabled)
      await fetchProviders()
    } catch {
      error.value = '无法更新供应商状态'
    }
  }

  return {
    providers,
    loading,
    error,
    testResult,
    hasProviders,
    defaultProvider,
    enabledProviders,
    fetchProviders,
    createProvider,
    updateProvider,
    deleteProvider,
    testConnection,
    setDefault,
    updateEnabled,
  }
})
