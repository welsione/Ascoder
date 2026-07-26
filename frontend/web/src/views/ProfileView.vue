<template>
  <div class="profile-container">
    <el-card class="profile-card">
      <template #header>
        <div class="card-header">
          <h2>个人中心</h2>
        </div>
      </template>

      <!-- 基本信息 -->
      <div class="profile-section">
        <h3 class="section-title">基本信息</h3>
        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="80px"
          @submit.prevent="handleUpdateProfile">
          <el-form-item label="用户名">
            <el-input :model-value="authStore.user?.username" disabled />
          </el-form-item>
          <el-form-item label="角色">
            <div class="role-tags">
              <el-tag v-for="role in authStore.user?.roles" :key="role" size="small" :type="role === 'ADMIN' ? 'danger' : ''">
                {{ roleName(role) }}
              </el-tag>
            </div>
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" placeholder="输入昵称" clearable />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" placeholder="输入邮箱（可选）" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="profileLoading" @click="handleUpdateProfile">保存修改</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 修改密码 -->
      <div class="profile-section">
        <h3 class="section-title">修改密码</h3>
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="80px"
          @submit.prevent="handleChangePassword">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" placeholder="输入原密码" show-password />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" placeholder="至少 8 位，含字母和数字" show-password />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" placeholder="确认新密码" show-password
              @keyup.enter="handleChangePassword" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordLoading" @click="handleChangePassword">修改密码</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { type FormInstance, type FormRules } from 'element-plus'
import { useNotify } from '../composables/useNotify'
import { useAuthStore } from '../stores/auth'

const notify = useNotify()
const authStore = useAuthStore()
const profileFormRef = ref<FormInstance>()
const passwordFormRef = ref<FormInstance>()
const profileLoading = ref(false)
const passwordLoading = ref(false)

const profileForm = reactive({
  nickname: '',
  email: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const profileRules: FormRules = {
  email: [
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' },
  ],
}

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, max: 128, message: '密码长度 8-128 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

function roleName(code: string): string {
  const map: Record<string, string> = { ADMIN: '管理员', USER: '普通用户' }
  return map[code] ?? code
}

function initForm() {
  if (authStore.user) {
    profileForm.nickname = authStore.user.nickname ?? ''
    profileForm.email = authStore.user.email ?? ''
  }
}

onMounted(() => {
  initForm()
})

async function handleUpdateProfile() {
  if (!profileFormRef.value) return
  await profileFormRef.value.validate(async (valid) => {
    if (!valid) return
    profileLoading.value = true
    try {
      await authStore.updateProfile({
        nickname: profileForm.nickname || undefined,
        email: profileForm.email || undefined,
      })
      notify.success('个人信息已更新')
    } catch {
      notify.error(new Error(authStore.error || '更新失败'), '更新失败')
    } finally {
      profileLoading.value = false
    }
  })
}

async function handleChangePassword() {
  if (!passwordFormRef.value) return
  await passwordFormRef.value.validate(async (valid) => {
    if (!valid) return
    passwordLoading.value = true
    try {
      await authStore.changePassword({
        oldPassword: passwordForm.oldPassword,
        newPassword: passwordForm.newPassword,
      })
      notify.success('密码修改成功，请重新登录')
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      passwordFormRef.value?.resetFields()
      // 改密后需要重新登录
      await authStore.logout()
      window.location.href = '/login'
    } catch {
      notify.error(new Error(authStore.error || '修改密码失败'), '修改密码失败')
    } finally {
      passwordLoading.value = false
    }
  })
}
</script>

<style scoped>
.profile-container {
  display: flex;
  justify-content: center;
  padding: 40px 20px;
}

.profile-card {
  width: 560px;
  max-width: 100%;
}

.card-header h2 {
  margin: 0;
}

.profile-section {
  margin-bottom: 32px;
}

.profile-section:last-child {
  margin-bottom: 0;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.role-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  line-height: 24px;
}
</style>
