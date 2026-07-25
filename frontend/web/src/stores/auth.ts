import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as authApi from '../services/authApi'
import type { UserInfo, LoginRequest, RegisterRequest } from '../types/auth'

const REFRESH_TOKEN_KEY = 'ascoder-refresh-token'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<UserInfo | null>(null)
  const accessToken = ref<string | null>(null)
  const refreshToken = ref<string | null>(localStorage.getItem(REFRESH_TOKEN_KEY))
  const tokenExpiresAt = ref<number>(0)
  const loading = ref(false)
  const error = ref('')

  const isAuthenticated = computed(() => !!accessToken.value)
  const isAdmin = computed(() => user.value?.roles.includes('ADMIN') ?? false)

  function setTokens(auth: { accessToken: string; refreshToken: string; expiresIn: number }) {
    accessToken.value = auth.accessToken
    refreshToken.value = auth.refreshToken
    tokenExpiresAt.value = Date.now() + auth.expiresIn * 1000
    localStorage.setItem(REFRESH_TOKEN_KEY, auth.refreshToken)
  }

  function clearTokens() {
    accessToken.value = null
    refreshToken.value = null
    tokenExpiresAt.value = 0
    user.value = null
    localStorage.removeItem(REFRESH_TOKEN_KEY)
  }

  async function login(req: LoginRequest) {
    loading.value = true
    error.value = ''
    try {
      const res = await authApi.login(req)
      setTokens(res)
      user.value = res.user
    } catch (err) {
      error.value = err instanceof Error ? err.message : '登录失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function register(req: RegisterRequest) {
    loading.value = true
    error.value = ''
    try {
      const res = await authApi.register(req)
      setTokens(res)
      user.value = res.user
    } catch (err) {
      error.value = err instanceof Error ? err.message : '注册失败'
      throw err
    } finally {
      loading.value = false
    }
  }

  async function refresh(): Promise<string> {
    if (!refreshToken.value) {
      throw new Error('No refresh token')
    }
    const res = await authApi.refresh({ refreshToken: refreshToken.value })
    setTokens(res)
    if (!user.value) {
      user.value = res.user
    }
    return res.accessToken
  }

  async function logout() {
    if (refreshToken.value) {
      try {
        await authApi.logout({ refreshToken: refreshToken.value })
      } catch {
        // 忽略登出失败
      }
    }
    clearTokens()
  }

  async function fetchMe() {
    try {
      user.value = await authApi.getCurrentUser()
    } catch {
      clearTokens()
      throw new Error('获取用户信息失败')
    }
  }

  function hasPermission(code: string): boolean {
    if (!user.value) return false
    if (user.value.roles.includes('ADMIN')) return true
    return user.value.permissions.includes(code)
  }

  return {
    user,
    accessToken,
    refreshToken,
    tokenExpiresAt,
    loading,
    error,
    isAuthenticated,
    isAdmin,
    login,
    register,
    refresh,
    logout,
    fetchMe,
    setTokens,
    clearTokens,
    hasPermission,
  }
})
