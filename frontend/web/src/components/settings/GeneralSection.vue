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
  if (s.valueType === 'INT' || s.valueType === 'LONG' || s.valueType === 'DOUBLE') {
    return Number(s.value)
  }
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
  if (s.valueType === 'INT' || s.valueType === 'LONG' || s.valueType === 'DOUBLE') {
    // 数值类型用 Number 归一化比较，避免 null/空串被一边转 0、另一边转 '' 导致误判 dirty
    return Number(current) !== Number(s.value)
  }
  return String(current ?? '') !== String(s.value ?? '')
}
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">通用设置</p>
        <h2>运行时调参与默认值管理</h2>
      </div>
      <div class="section-actions">
        <el-button circle :loading="store.loading" title="刷新" aria-label="刷新" @click="store.fetchAll">
          <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
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

      <div class="settings-form-grid config-list">
        <div
          v-for="s in groupedSettings[cat.key]"
          :key="s.key"
          class="config-item"
          :class="{ 'is-dirty': isDirty(s) }"
        >
          <label class="field-label">
            {{ s.key }}
            <el-tag v-if="s.overridden" size="small" type="warning">已覆盖</el-tag>
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
            :min="s.valueType === 'DOUBLE' ? 0 : 1"
            :step="s.valueType === 'DOUBLE' ? 0.01 : 1"
          />
          <!-- STRING：text -->
          <el-input
            v-else
            v-model="draft[s.key]"
            :placeholder="`默认: ${s.defaultValue}`"
          />

          <div class="config-item-footer">
            <p class="field-meta">
              <span>{{ s.valueType }}</span>
              <span>当前 <code>{{ formatValue(s) }}</code></span>
              <span>默认 <code>{{ s.defaultValue }}</code></span>
            </p>
            <Transition name="config-save">
              <el-button
                v-if="isDirty(s)"
                type="primary"
                size="small"
                :loading="savingKey === s.key"
                @click="handleSave(s.key)"
              >
                保存
              </el-button>
            </Transition>
          </div>
        </div>
      </div>
    </div>

    <el-alert v-if="store.error" type="error" :title="store.error" show-icon :closable="false" />
  </section>
</template>

<style scoped>
.settings-category-block {
  border-top: 1px solid var(--stroke);
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
  font-weight: var(--font-weight-semibold);
  margin: 0 0 var(--spacing-1);
}
.settings-category-desc {
  margin: 0;
  color: var(--muted);
  font-size: var(--font-size-sm);
}
.settings-category-hint {
  margin: var(--spacing-2) 0 0;
  color: var(--muted);
  font-size: var(--font-size-xs);
  display: inline-flex;
  align-items: center;
  gap: var(--spacing-1);
}

/* 分组列表：一个分类的配置项收进圆角卡片，项间用分隔线（Apple 分组列表） */
.config-list {
  gap: 0;
  border: 1px solid var(--stroke);
  border-radius: var(--radius-lg);
  background: var(--surface);
  overflow: hidden;
}

/* 配置项：垂直布局，聚焦/dirty 时背景反馈 */
.config-item {
  display: flex;
  flex-direction: column;
  gap: var(--spacing-2);
  padding: var(--spacing-4);
  border-top: 1px solid var(--stroke);
  transition: background var(--transition-normal);
}
.config-item:first-child {
  border-top: 0;
}
.config-item:focus-within {
  background: var(--surface-soft);
}
/* dirty：accent-soft 背景，提示已修改未保存 */
.config-item.is-dirty {
  background: var(--chat-accent-soft);
}
.config-item.is-dirty:focus-within {
  background: var(--chat-accent-hover);
}

/* 已覆盖标签对齐 */
.config-item .field-label .el-tag {
  margin-left: var(--spacing-2);
  vertical-align: middle;
}

/* el-input-number 撑满 */
.config-item :deep(.el-input-number) {
  width: 100%;
}

/* 底部：meta + 保存按钮 */
.config-item-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--spacing-3);
  flex-wrap: wrap;
}
.config-item-footer .field-meta {
  display: flex;
  align-items: center;
  gap: var(--spacing-3);
  flex-wrap: wrap;
}

/* 保存按钮平滑出现 */
.config-save-enter-active,
.config-save-leave-active {
  transition:
    opacity var(--transition-normal),
    transform var(--transition-normal);
}
.config-save-enter-from,
.config-save-leave-to {
  opacity: 0;
  transform: translateX(-8px);
}

@media (prefers-reduced-motion: reduce) {
  .config-save-enter-active,
  .config-save-leave-active {
    transition: opacity var(--transition-fast);
  }
  .config-save-enter-from,
  .config-save-leave-to {
    transform: none;
  }
}
</style>