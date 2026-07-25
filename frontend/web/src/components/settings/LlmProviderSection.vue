<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshCw, Cpu, Plug } from 'lucide-vue-next'
import { useLlmProviderStore } from '../../stores/llmProvider'
import type { LlmProvider, LlmProviderType, CreateLlmProviderRequest } from '../../types/llmProvider'

const store = useLlmProviderStore()

const drawerVisible = ref(false)
const editingId = ref<number | null>(null)
const testingId = ref<number | null>(null)

const initialForm = (): CreateLlmProviderRequest & { enabled?: boolean } => ({
  name: '',
  providerType: 'OPENAI_COMPATIBLE',
  apiKey: '',
  baseUrl: '',
  modelId: '',
  maxTokens: undefined,
  timeoutSeconds: 240,
  isDefault: false,
  enabled: true,
})

const form = ref<CreateLlmProviderRequest & { enabled?: boolean }>(initialForm())

const providerTypeOptions: { label: string; value: LlmProviderType }[] = [
  { label: 'OpenAI 兼容', value: 'OPENAI_COMPATIBLE' },
  { label: 'Anthropic 兼容', value: 'ANTHROPIC_COMPATIBLE' },
]

onMounted(() => {
  store.fetchProviders()
})

function maskApiKey(key: string): string {
  if (!key || key.length <= 8) return '••••••••'
  return key.slice(0, 4) + '••••••••' + key.slice(-4)
}

function openCreate() {
  editingId.value = null
  form.value = initialForm()
  drawerVisible.value = true
}

function closeDrawer() {
  drawerVisible.value = false
}

function openEdit(provider: LlmProvider) {
  editingId.value = provider.id
  form.value = {
    name: provider.name,
    providerType: provider.providerType,
    apiKey: '',
    baseUrl: provider.baseUrl,
    modelId: provider.modelId,
    maxTokens: provider.maxTokens ?? undefined,
    timeoutSeconds: provider.timeoutSeconds ?? 240,
    isDefault: provider.isDefault,
    enabled: provider.enabled,
  }
  drawerVisible.value = true
}

function onDrawerClosed() {
  editingId.value = null
  form.value = initialForm()
}

async function handleSubmit() {
  if (!form.value.name || !form.value.baseUrl || !form.value.modelId) {
    ElMessage.warning('请填写名称、Base URL 和模型 ID')
    return
  }
  // 编辑时 apiKey 为空则保留原值（不发送空字符串覆盖）
  const payload = { ...form.value }
  if (editingId.value && !payload.apiKey) {
    delete (payload as Partial<CreateLlmProviderRequest>).apiKey
  }

  if (editingId.value) {
    const result = await store.updateProvider(editingId.value, payload as CreateLlmProviderRequest)
    if (result) {
      ElMessage.success('供应商已更新')
      drawerVisible.value = false
    }
  } else {
    if (!payload.apiKey) {
      ElMessage.warning('新增供应商时 API Key 不能为空')
      return
    }
    const result = await store.createProvider(payload as CreateLlmProviderRequest)
    if (result) {
      ElMessage.success('供应商已创建')
      drawerVisible.value = false
    }
  }
}

async function handleDelete(provider: LlmProvider) {
  if (provider.builtin) {
    ElMessage.warning('内置供应商不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除供应商「${provider.name}」？`, '删除确认', { type: 'warning' })
    await store.deleteProvider(provider.id)
  } catch {
    // 用户取消
  }
}

async function handleTestConnection(provider: LlmProvider) {
  testingId.value = provider.id
  await store.testConnection(provider.id)
  testingId.value = null
  if (store.testResult?.success) {
    ElMessage.success(`连接成功 (${store.testResult.latencyMs}ms)`)
  } else {
    ElMessage.error(store.testResult?.message ?? '连接失败')
  }
}

async function handleSetDefault(provider: LlmProvider) {
  if (provider.isDefault) return
  await store.setDefault(provider.id)
}

async function handleToggleEnabled(provider: LlmProvider, enabled: boolean) {
  await store.updateEnabled(provider.id, enabled)
}
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">LLM 供应商</p>
        <h2>管理 LLM 供应商配置与连接状态</h2>
      </div>
      <div class="section-actions">
        <el-button circle :loading="store.loading" title="刷新" aria-label="刷新" @click="store.fetchProviders()">
          <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
        </el-button>
        <el-button type="primary" @click="openCreate">
          <Cpu class="button-icon" :size="16" :stroke-width="1.8" />
          新增供应商
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="!store.hasProviders && !store.loading"
      type="warning"
      title="尚未配置 LLM 供应商"
      description="请先添加至少一个 LLM 供应商，否则问答功能将无法使用。"
      show-icon
      :closable="false"
    />

    <el-table v-loading="store.loading" :data="store.providers" empty-text="暂无 LLM 供应商">
      <el-table-column label="名称" min-width="140">
        <template #default="{ row }">
          <span class="cell-name">{{ row.name }}</span>
          <el-tag v-if="row.isDefault" size="small" type="success" class="cell-tag">默认</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="160">
        <template #default="{ row }">
          <el-tag size="small" :type="row.providerType === 'ANTHROPIC_COMPATIBLE' ? 'warning' : 'info'">
            {{ row.providerType === 'ANTHROPIC_COMPATIBLE' ? 'Anthropic' : 'OpenAI' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="modelId" label="模型" min-width="160" show-overflow-tooltip />
      <el-table-column label="API Key" width="160">
        <template #default="{ row }">
          <code class="cell-code">{{ maskApiKey(row.apiKey) }}</code>
        </template>
      </el-table-column>
      <el-table-column label="默认" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.isDefault" @change="handleSetDefault(row)" />
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" @change="handleToggleEnabled(row, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="内置" width="60">
        <template #default="{ row }">
          <el-tag v-if="row.builtin" size="small" type="info">是</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text size="small" @click="openEdit(row)">编辑</el-button>
          <el-button text size="small" :loading="testingId === row.id" @click="handleTestConnection(row)">
            <Plug class="button-icon" :size="14" :stroke-width="1.8" />测试
          </el-button>
          <el-button text size="small" :disabled="row.builtin" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-alert v-if="store.error" type="error" :title="store.error" show-icon :closable="false" />
  </section>

  <!-- 新增/编辑供应商抽屉 -->
  <el-drawer
    v-model="drawerVisible"
    :title="editingId ? '编辑供应商' : '新增供应商'"
    direction="rtl"
    size="480px"
    destroy-on-close
    @closed="onDrawerClosed"
  >
    <div class="drawer-form">
      <p class="field-section-title">基础信息</p>
      <div class="settings-form-grid">
        <div>
          <label class="field-label">名称</label>
          <el-input v-model="form.name" placeholder="例如 MiniMax" />
        </div>
        <div>
          <label class="field-label">供应商类型</label>
          <el-select v-model="form.providerType">
            <el-option v-for="opt in providerTypeOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
        </div>
        <div>
          <label class="field-label">启用</label>
          <div class="switch-wrap"><el-switch v-model="form.enabled" /></div>
        </div>
        <div>
          <label class="field-label">设为默认</label>
          <div class="switch-wrap"><el-switch v-model="form.isDefault" /></div>
        </div>
      </div>

      <p class="field-section-title">连接参数</p>
      <div class="settings-form-grid">
        <div class="span-2">
          <label class="field-label">Base URL</label>
          <el-input v-model="form.baseUrl" placeholder="https://api.example.com/v1" />
        </div>
        <div>
          <label class="field-label">模型 ID</label>
          <el-input v-model="form.modelId" placeholder="gpt-4o" />
        </div>
        <div>
          <label class="field-label">API Key</label>
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="editingId ? '留空保留原值' : '输入 API Key'"
          />
        </div>
      </div>

      <p class="field-section-title">高级参数（可选）</p>
      <div class="settings-form-grid">
        <div>
          <label class="field-label">maxTokens</label>
          <el-input-number v-model="form.maxTokens" :min="1" :max="999999" />
        </div>
        <div>
          <label class="field-label">timeoutSeconds</label>
          <el-input-number v-model="form.timeoutSeconds" :min="1" :max="3600" />
        </div>
      </div>

      <el-alert v-if="store.error" type="error" :title="store.error" show-icon :closable="false" />
    </div>

    <template #footer>
      <el-button @click="closeDrawer">取消</el-button>
      <el-button type="primary" @click="handleSubmit">
        {{ editingId ? '保存' : '创建' }}
      </el-button>
    </template>
  </el-drawer>
</template>
