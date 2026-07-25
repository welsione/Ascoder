<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <span class="user-menu-trigger">
      <el-avatar :size="32" :icon="UserFilled" />
      <span class="user-name">{{ displayName }}</span>
      <el-tag v-if="authStore.isAdmin" size="small" type="danger">管理员</el-tag>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '用户')

async function handleCommand(command: string) {
  if (command === 'logout') {
    await authStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  }
}
</script>

<style scoped>
.user-menu-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 0 8px;
}
.user-name {
  font-size: 14px;
}
</style>
