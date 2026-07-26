<template>
  <AuthLayout>
    <div class="auth-card">
      <!-- 表单头部 -->
      <div class="auth-card-header">
        <div class="alert-badge">
          <Lock class="alert-icon" />
          <span>安全提示</span>
        </div>
        <h2 class="auth-title">修改密码</h2>
        <p class="auth-desc">首次登录需要修改默认密码后方可使用系统</p>
      </div>

      <!-- 表单主体 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="handleSubmit"
        class="auth-form">
        <el-form-item prop="oldPassword">
          <div class="field">
            <label class="field-label">原密码</label>
            <el-input v-model="form.oldPassword" type="password" placeholder="输入原密码" size="large"
              :prefix-icon="Lock" show-password />
          </div>
        </el-form-item>
        <el-form-item prop="newPassword">
          <div class="field">
            <label class="field-label">新密码</label>
            <el-input v-model="form.newPassword" type="password" placeholder="至少 8 位，含字母和数字" size="large"
              :prefix-icon="Lock" show-password />
          </div>
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <div class="field">
            <label class="field-label">确认新密码</label>
            <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入新密码" size="large"
              :prefix-icon="Lock" show-password @keyup.enter="handleSubmit" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="auth-submit" :loading="authStore.loading"
            @click="handleSubmit">确认修改</el-button>
        </el-form-item>
      </el-form>

      <!-- 底部操作 -->
      <div class="auth-footer">
        <el-button link class="logout-link" @click="handleLogout">退出登录</el-button>
      </div>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import AuthLayout from '../components/auth/AuthLayout.vue'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== form.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
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

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await authStore.changePassword({
        oldPassword: form.oldPassword,
        newPassword: form.newPassword,
      })
      ElMessage.success('密码修改成功')
      router.push('/')
    } catch {
      ElMessage.error(authStore.error || '修改密码失败')
    }
  })
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.auth-card {
  padding: var(--spacing-8) var(--spacing-6);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-xl);
  background: var(--surface);
  box-shadow: var(--shadow-panel), var(--highlight-edge);
}

.auth-card-header {
  display: grid;
  gap: var(--spacing-2);
  margin-bottom: var(--spacing-6);
}

.alert-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-2);
  width: fit-content;
  padding: var(--spacing-1) var(--spacing-3);
  border: 1px solid rgba(217, 119, 6, 0.2);
  border-radius: var(--radius-full);
  background: rgba(217, 119, 6, 0.06);
  color: var(--warning);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-semibold);
  letter-spacing: var(--tracking-wide);
}

.alert-icon {
  width: 14px;
  height: 14px;
}

.auth-title {
  margin: 0;
  font-size: var(--font-size-3xl);
  font-weight: var(--font-weight-bold);
  letter-spacing: var(--tracking-tighter);
  line-height: var(--line-height-display);
  color: var(--text);
}

.auth-desc {
  margin: 0;
  font-size: var(--font-size-md);
  color: var(--muted);
  line-height: var(--line-height-normal);
}

.auth-form {
  display: grid;
  gap: var(--spacing-1);
}

.auth-form .el-form-item {
  margin-bottom: var(--spacing-4);
}

.field {
  display: grid;
  gap: var(--spacing-1);
  width: 100%;
}

.field-label {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--subtle);
  letter-spacing: var(--tracking-wide);
  transition: color var(--transition-fast);
}

.field:focus-within .field-label {
  color: var(--chat-accent);
}

.auth-submit {
  width: 100%;
  height: 44px;
  border-radius: var(--radius-full);
  font-weight: var(--font-weight-semibold);
  font-size: var(--font-size-md);
  margin-top: var(--spacing-2);
}

.auth-footer {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: var(--spacing-5);
  padding-top: var(--spacing-5);
  border-top: 1px solid var(--stroke);
}

.logout-link {
  font-size: var(--font-size-sm);
  color: var(--muted);
  font-weight: var(--font-weight-medium);
}

.logout-link:hover {
  color: var(--danger);
}
</style>
