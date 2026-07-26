<template>
  <AuthLayout>
    <div class="auth-card">
      <!-- 表单头部 -->
      <div class="auth-card-header">
        <h2 class="auth-title">创建账号</h2>
        <p class="auth-desc">注册以开始使用 Ascoder</p>
      </div>

      <!-- 表单主体 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="handleRegister"
        class="auth-form">
        <el-form-item prop="username">
          <div class="field">
            <label class="field-label">用户名</label>
            <el-input v-model="form.username" placeholder="3-64 个字符" size="large" :prefix-icon="User" />
          </div>
        </el-form-item>
        <el-form-item prop="password">
          <div class="field">
            <label class="field-label">密码</label>
            <el-input v-model="form.password" type="password" placeholder="至少 8 位，含字母和数字" size="large"
              :prefix-icon="Lock" show-password />
          </div>
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <div class="field">
            <label class="field-label">确认密码</label>
            <el-input v-model="form.confirmPassword" type="password" placeholder="再次输入密码" size="large"
              :prefix-icon="Lock" show-password @keyup.enter="handleRegister" />
          </div>
        </el-form-item>
        <el-form-item prop="nickname">
          <div class="field">
            <label class="field-label">昵称 <span class="optional-tag">可选</span></label>
            <el-input v-model="form.nickname" placeholder="显示名称" size="large" :prefix-icon="UserFilled" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="auth-submit" :loading="authStore.loading"
            @click="handleRegister">注册</el-button>
        </el-form-item>
      </el-form>

      <!-- 底部链接 -->
      <div class="auth-footer">
        <span class="auth-footer-text">已有账号？</span>
        <router-link to="/login" class="auth-link">立即登录</router-link>
      </div>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { User, Lock, UserFilled } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import AuthLayout from '../components/auth/AuthLayout.vue'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
})

const validateConfirmPassword = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 64, message: '用户名长度 3-64 个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, max: 128, message: '密码长度 8-128 个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

async function handleRegister() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await authStore.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname || undefined,
      })
      router.push('/')
    } catch {
      ElMessage.error(authStore.error || '注册失败')
    }
  })
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

.optional-tag {
  display: inline-block;
  padding: 0 var(--spacing-1);
  border-radius: var(--radius-xs);
  background: var(--surface-soft);
  color: var(--muted);
  font-size: var(--font-size-xs);
  font-weight: var(--font-weight-medium);
  letter-spacing: 0;
  vertical-align: middle;
  margin-left: var(--spacing-1);
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
  gap: var(--spacing-1);
  margin-top: var(--spacing-5);
  padding-top: var(--spacing-5);
  border-top: 1px solid var(--stroke);
}

.auth-footer-text {
  font-size: var(--font-size-sm);
  color: var(--muted);
}

.auth-link {
  font-size: var(--font-size-sm);
  font-weight: var(--font-weight-semibold);
  color: var(--chat-accent);
  text-decoration: none;
  transition: color var(--transition-fast);
}

.auth-link:hover {
  text-decoration: underline;
}
</style>
