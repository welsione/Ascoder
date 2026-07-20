<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshCw, SlidersHorizontal, RotateCcw } from 'lucide-vue-next'
import { useRuntimeSettingStore } from '../../stores/runtimeSetting'
import type { RuntimeSetting, RuntimeSettingCategory } from '../../types/runtimeSetting'

const store = useRuntimeSettingStore()

// key → 当前输入值（编辑缓冲）
const draft = reactive<Record<string, string | boolean | number>>({})

const categoryMeta: { key: RuntimeSettingCategory; title: string; description: string; restartHint?: boolean }[] = [
  { key: 'agent', title: 'Agent 行为调参', description: '迭代次数、超时、规划与查询规划阈值。修改后立即对新建任务生效。', restartHint: true },
  { key: 'codegraph', title: 'CodeGraph', description: 'CLI 命令超时。executable 需重启后生效。' },
  { key: 'git', title: 'Git', description: 'git 命令超时。修改后立即对下一次 git 命令生效。' },
]

const savingKey = ref<string | null>(null)

onMounted(() => {
  store.fetchAll().then(() => {
    for (const s of store.settings) {
      draft[s.key] = parseDraftValue(s)
    }
  })
})

function parseDraftValue(s: RuntimeSetting): string | boolean | number {
  if (s.valueType === 'BOOLEAN') return s.value === 'true'
  if (s.valueType === 'INT' || s.valueType === 'LONG') return Number(s.value)
  return s.value ?? ''
}

function formatValue(s: RuntimeSetting): string {
  if (s.value === null || s.value === undefined) return '—'
  return s.value
}

const groupedSettings = computed(() => {
  const map: Record<string, RuntimeSetting[]> = { agent: [], codegraph: [], git: [] }
  for (const s of store.settings) {
    if (map[s.category]) map[s.category].push(s)
  }
  return map
})

async function handleSave(key: string) {
  savingKey.value = key
  try {
    const v = draft[key]
    await store.update(key, String(v))
    ElMessage.success(`已保存：${key}`)
  } catch {
    ElMessage.error(store.error || '保存失败')
  } finally {
    savingKey.value = null
  }
}

async function handleReset(category: RuntimeSettingCategory) {
  try {
    await store.resetCategory(category)
    for (const s of store.settingsOf(category)) {
      draft[s.key] = parseDraftValue(s)
    }
    ElMessage.success(`已恢复「${category}」分类的默认值`)
  } catch {
    ElMessage.error(store.error || '恢复默认失败')
  }
}

function isDirty(s: RuntimeSetting): boolean {
  const current = draft[s.key]
  if (current === undefined) return false
  if (s.valueType === 'BOOLEAN') return Boolean(current) !== (s.value === 'true')
  return String(current) !== (s.value ?? '')
}
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">通用设置</p>
        <h2>运行时调参与默认值管理</h2>
      </div>
      <div style="display:flex;gap:8px;">
        <el-button circle :loading="store.loading" title="刷新" @click="store.fetchAll">
          <RefreshCw :size="16" :stroke-width="1.8" />
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="!store.loading && store.settings.length === 0"
      type="info"
      title="尚未加载任何运行时配置"
      description="刷新页面或检查后端 /api/settings 是否可访问。"
      show-icon
      :closable="false"
      style="margin-bottom:12px;"
    />

    <div v-for="cat in categoryMeta" :key="cat.key" class="settings-category-block">
      <div class="settings-category-header">
        <div>
          <h3 class="settings-category-title">{{ cat.title }}</h3>
          <p class="settings-category-desc">{{ cat.description }}</p>
          <p v-if="cat.restartHint" class="settings-category-hint">
            <SlidersHorizontal :size="12" :stroke-width="1.8" />
            部分项（如 SSE 线程池 core/max/queue）需重启进程生效。
          </p>
        </div>
        <el-button text :loading="store.saving" @click="handleReset(cat.key)">
          <RotateCcw class="button-icon" :size="14" :stroke-width="1.8" />
          恢复该分类默认
        </el-button>
      </div>

      <div class="settings-form-grid">
        <div v-for="s in groupedSettings[cat.key]" :key="s.key" class="settings-form-cell">
          <label class="field-label">
            {{ s.key }}
            <el-tag v-if="s.overridden" size="small" type="warning" style="margin-left:6px;">已覆盖</el-tag>
          </label>
          <p class="field-desc">{{ s.description }}</p>

          <!-- BOOLEAN：switch -->
          <el-switch
            v-if="s.valueType === 'BOOLEAN'"
            v-model="draft[s.key]"
          />
          <!-- INT / LONG / DOUBLE：number -->
          <el-input-number
            v-else-if="s.valueType === 'INT' || s.valueType === 'LONG' || s.valueType === 'DOUBLE'"
            v-model="draft[s.key]"
            :min="1"
            :step="s.valueType === 'DOUBLE' ? 0.01 : 1"
            style="width:100%;"
          />
          <!-- STRING：text -->
          <el-input
            v-else
            v-model="draft[s.key]"
            :placeholder="`默认: ${s.defaultValue}`"
          />

          <p class="field-meta">
            类型: {{ s.valueType }} · 当前值: <code>{{ formatValue(s) }}</code> · 默认: <code>{{ s.defaultValue }}</code>
          </p>

          <el-button
            v-if="isDirty(s)"
            type="primary"
            size="small"
            :loading="savingKey === s.key"
            @click="handleSave(s.key)"
          >
            保存
          </el-button>
        </div>
      </div>
    </div>

    <el-alert v-if="store.error" type="error" :title="store.error" show-icon :closable="false" />
  </section>
</template>

<style scoped>
.settings-category-block {
  border-top: 1px solid var(--surface-border);
  padding-top: var(--spacing-5);
  margin-top: var(--spacing-5);
}
.settings-category-block:first-of-type {
  border-top: 0;
  padding-top: 0;
  margin-top: 0;
}
.settings-category-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--spacing-4);
  margin-bottom: var(--spacing-4);
}
.settings-category-title {
  font-size: var(--font-size-lg);
  font-weight: 600;
  margin: 0 0 4px;
}
.settings-category-desc {
  margin: 0;
  color: var(--muted);
  font-size: var(--font-size-sm);
}
.settings-category-hint {
  margin: 6px 0 0;
  color: var(--muted);
  font-size: var(--font-size-xs);
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.settings-form-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.field-desc {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--muted);
}
.field-meta {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--muted);
}
.field-meta code {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>