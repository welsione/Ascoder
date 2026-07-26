import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as userApi from '../services/userApi'
import type { UserSummary } from '../services/userApi'

export const useUserStore = defineStore('user', () => {
  const users = ref<UserSummary[]>([])
  const loading = ref(false)
  const error = ref('')

  async function fetchAll() {
    loading.value = true
    error.value = ''
    try {
      users.value = await userApi.listUsers()
    } catch (err) {
      error.value = err instanceof Error ? err.message : '加载用户列表失败'
    } finally {
      loading.value = false
    }
  }

  return { users, loading, error, fetchAll }
})
