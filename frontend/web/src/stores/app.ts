import { defineStore } from 'pinia'
import { ref, computed, watch } from 'vue'
import { getHealth } from '../services/healthApi'
import type { HealthResponse } from '../types/common'
import { extractErrorMessage } from '../composables/useCrudStore'

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false)
  const health = ref<HealthResponse | null>(null)
  const loading = ref(false)
  const error = ref('')

  const theme = ref<'light' | 'dark' | 'system'>(
    (localStorage.getItem('ascoder-theme') as 'light' | 'dark' | 'system') || 'system'
  )

  const resolvedTheme = computed<'light' | 'dark'>(() => {
    if (theme.value !== 'system') return theme.value
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
  })

  function applyTheme() {
    document.documentElement.setAttribute('data-theme', resolvedTheme.value)
    localStorage.setItem('ascoder-theme', theme.value)
  }

  function setTheme(t: 'light' | 'dark' | 'system') {
    theme.value = t
  }

  function toggleTheme() {
    const order: Array<'light' | 'dark' | 'system'> = ['light', 'dark', 'system']
    const idx = order.indexOf(theme.value)
    theme.value = order[(idx + 1) % order.length]
  }

  watch(theme, applyTheme)
  applyTheme()

  window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
    if (theme.value === 'system') applyTheme()
  })

  const healthStatus = computed<'ok' | 'fail' | 'idle'>(() => {
    if (health.value) return 'ok'
    if (error.value) return 'fail'
    return 'idle'
  })

  const healthLabel = computed(() => {
    if (health.value) return '后端已连接'
    if (error.value) return '后端连接失败'
    return '等待检查'
  })

  async function check() {
    loading.value = true
    error.value = ''
    try {
      health.value = await getHealth()
    } catch (err) {
      error.value = extractErrorMessage(err, '无法连接后端服务')
      health.value = null
    } finally {
      loading.value = false
    }
  }

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  return {
    sidebarCollapsed,
    health,
    loading,
    error,
    healthStatus,
    healthLabel,
    check,
    toggleSidebar,
    theme,
    setTheme,
    toggleTheme,
    resolvedTheme,
  }
})
