<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, Cpu, Plug } from 'lucide-vue-next'
import { useLlmProviderStore } from '../../stores/llmProvider'
import type { LlmProvider, LlmProviderType, CreateLlmProviderRequest } from '../../types/llmProvider'

const store = useLlmProviderStore()

const showForm = ref(false)
const editingId = ref<number | null>(null)
const testingId = ref<number | null>(null)

const form = ref<CreateLlmProviderRequest & { enabled?: boolean }>({
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
  form.value = {
    name: '',
    providerType: 'OPENAI_COMPATIBLE',
    apiKey: '',
    baseUrl: '',
    modelId: '',
    maxTokens: undefined,
    timeoutSeconds: 240,
    isDefault: false,
    enabled: true,
  }
  showForm.value = true
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
  showForm.value = true
}

function closeForm() {
  showForm.value = false
  editingId.value = null
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
      closeForm()
    }
  } else {
    if (!payload.apiKey) {
      ElMessage.warning('新增供应商时 API Key 不能为空')
      return
    }
    const result = await store.createProvider(payload as CreateLlmProviderRequest)
    if (result) {
      ElMessage.success('供应商已创建')
      closeForm()
    }
  }
}

async function handleDelete(provider: LlmProvider) {
  if (provider.builtin) {
    ElMessage.warning('内置供应商不可删除')
    return
  }
  await store.deleteProvider(provider.id)
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
      <div style="display:flex;gap:8px;">
        <el-button circle :loading="store.loading" title="刷新" @click="store.fetchProviders()">
          <RefreshCw :size="16" :stroke-width="1.8" />
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
      style="margin-bottom:12px;"
    />

    <el-table v-loading="store.loading" :data="store.providers" empty-text="暂无 LLM 供应商">
      <el-table-column label="名称" min-width="140">
        <template #default="{ row }">
          <span style="font-weight:600;">{{ row.name }}</span>
          <el-tag v-if="row.isDefault" size="small" type="success" style="margin-left:6px;">默认</el-tag>
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
          <code style="font-size:var(--font-size-xs);color:var(--muted);">{{ maskApiKey(row.apiKey) }}</code>
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
          <el-popconfirm
            title="确认删除此供应商？"
            confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="handleDelete(row)"
          >
            <template #reference>
              <el-button text size="small" :disabled="row.builtin" type="danger">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-alert v-if="store.error" type="error" :title="store.error" show-icon :closable="false" />
  </section>

  <!-- 新增/编辑表单 -->
  <section v-if="showForm" class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">{{ editingId ? '编辑供应商' : '新增供应商' }}</p>
        <h2>配置 LLM 供应商连接参数</h2>
      </div>
    </div>

    <p class="field-section-title">基础信息</p>
    <div class="settings-form-grid">
      <div>
        <label class="field-label">名称</label>
        <el-input v-model="form.name" placeholder="例如 MiniMax" />
      </div>
      <div>
        <label class="field-label">供应商类型</label>
        <el-select v-model="form.providerType" style="width:100%;">
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
        <el-input-number v-model="form.maxTokens" :min="1" :max="999999" style="width:100%;" />
      </div>
      <div>
        <label class="field-label">timeoutSeconds</label>
        <el-input-number v-model="form.timeoutSeconds" :min="1" :max="3600" style="width:100%;" />
      </div>
    </div>

    <div class="settings-actions">
      <el-button @click="closeForm">取消</el-button>
      <el-button type="primary" @click="handleSubmit">
        {{ editingId ? '保存' : '创建' }}
      </el-button>
    </div>

    <el-alert v-if="store.error" type="error" :title="store.error" show-icon :closable="false" />
  </section>
</template>

<style scoped>
.field-section-title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  margin: var(--spacing-5) 0 var(--spacing-2);
}
</style>
