<template>
  <AuthLayout>
    <div class="auth-card">
      <!-- 表单头部 -->
      <div class="auth-card-header">
        <h2 class="auth-title">欢迎回来</h2>
        <p class="auth-desc">登录你的 Ascoder 账号</p>
      </div>

      <!-- 表单主体 -->
      <el-form ref="formRef" :model="form" :rules="rules" label-width="0" @submit.prevent="handleLogin"
        class="auth-form">
        <el-form-item prop="username">
          <div class="field">
            <label class="field-label">用户名</label>
            <el-input v-model="form.username" placeholder="输入用户名" size="large" :prefix-icon="User" />
          </div>
        </el-form-item>
        <el-form-item prop="password">
          <div class="field">
            <label class="field-label">密码</label>
            <el-input v-model="form.password" type="password" placeholder="输入密码" size="large"
              :prefix-icon="Lock" show-password @keyup.enter="handleLogin" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="auth-submit" :loading="authStore.loading"
            @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>

      <!-- 底部链接 -->
      <div class="auth-footer">
        <span class="auth-footer-text">没有账号？</span>
        <router-link to="/register" class="auth-link">立即注册</router-link>
      </div>
    </div>
  </AuthLayout>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import AuthLayout from '../components/auth/AuthLayout.vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const formRef = ref<FormInstance>()

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    try {
      await authStore.login({ ...form })
      const redirect = (route.query.redirect as string) || '/'
      router.push(redirect)
    } catch {
      ElMessage.error(authStore.error || '登录失败')
    }
  })
}
</script>

<style scoped>
.auth-card {
  padding: var(--spacing-8);
  border: 1px solid var(--stroke);
  border-radius: var(--radius-2xl);
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
  font-size: 28px;
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
