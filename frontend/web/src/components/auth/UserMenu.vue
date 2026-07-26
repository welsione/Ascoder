<template>
  <el-dropdown trigger="click" @command="handleCommand">
    <span class="user-menu-trigger">
      <el-avatar :size="28" :icon="UserFilled" />
      <span class="user-name">{{ displayName }}</span>
    </span>
    <template #dropdown>
      <el-dropdown-menu>
        <el-dropdown-item command="profile">
          <UserCircle class="dropdown-icon" aria-hidden="true" :size="15" :stroke-width="1.8" />
          个人中心
        </el-dropdown-item>
        <el-dropdown-item command="change-password">
          <KeyRound class="dropdown-icon" aria-hidden="true" :size="15" :stroke-width="1.8" />
          修改密码
        </el-dropdown-item>
        <el-dropdown-item v-if="authStore.isAdmin" command="settings" divided>
          <Settings class="dropdown-icon" aria-hidden="true" :size="15" :stroke-width="1.8" />
          系统设置
        </el-dropdown-item>
        <el-dropdown-item :divided="!authStore.isAdmin" command="logout">
          <LogOut class="dropdown-icon" aria-hidden="true" :size="15" :stroke-width="1.8" />
          退出登录
        </el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { UserFilled } from '@element-plus/icons-vue'
import { KeyRound, LogOut, Settings, UserCircle } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../../stores/auth'

const authStore = useAuthStore()
const router = useRouter()

const displayName = computed(() => authStore.user?.nickname || authStore.user?.username || '用户')

async function handleCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
  } else if (command === 'change-password') {
    router.push('/change-password')
  } else if (command === 'settings') {
    router.push('/settings')
  } else if (command === 'logout') {
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
  gap: 6px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--radius-md, 6px);
  transition: background 0.15s;
}

.user-menu-trigger:hover {
  background: var(--el-fill-color-light);
}

.user-name {
  font-size: 13px;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.dropdown-icon {
  margin-right: 4px;
  vertical-align: -2px;
}
</style>
