<script setup lang="ts">
import { computed } from 'vue'
import { RefreshCw, ShieldAlert, SlidersHorizontal } from 'lucide-vue-next'
import { useAgentToolStore } from '../../stores/agentTool'
import type { AgentToolRiskLevel } from '../../types/agentTool'

const toolStore = useAgentToolStore()

const riskType = computed(() => {
  return (risk: AgentToolRiskLevel) => {
    if (risk === 'high') return 'danger'
    if (risk === 'medium') return 'warning'
    return 'success'
  }
})

const riskText = computed(() => {
  return (risk: AgentToolRiskLevel) => {
    if (risk === 'high') return '高风险'
    if (risk === 'medium') return '中风险'
    return '低风险'
  }
})
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">工具开关</p>
        <h2>控制 Agent 实际可调用的内置工具</h2>
      </div>
      <el-button
        circle
        :loading="toolStore.loading"
        title="刷新工具"
        aria-label="刷新工具"
        @click="toolStore.fetch"
      >
        <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
      </el-button>
    </div>

    <div class="tool-summary-grid">
      <div class="tool-summary-card">
        <span class="icon-badge" aria-hidden="true">
          <SlidersHorizontal :size="16" :stroke-width="1.8" />
        </span>
        <div>
          <p class="eyebrow">Enabled</p>
          <strong>{{ toolStore.enabledCount }} / {{ toolStore.totalCount }}</strong>
        </div>
      </div>
      <div class="tool-summary-card">
        <span class="icon-badge danger" aria-hidden="true">
          <ShieldAlert :size="16" :stroke-width="1.8" />
        </span>
        <div>
          <p class="eyebrow">Policy</p>
          <strong>运行时立即生效</strong>
        </div>
      </div>
    </div>

    <el-alert v-if="toolStore.error" type="error" :title="toolStore.error" show-icon :closable="false" />
  </section>

  <section
    v-for="group in toolStore.groupedTools"
    :key="group.name"
    class="surface-panel settings-block tool-group-panel"
  >
    <div class="section-heading compact">
      <div>
        <p class="kicker">{{ group.name }}</p>
        <h2>{{ group.enabledCount }} / {{ group.tools.length }} 个工具已启用</h2>
      </div>
    </div>

    <el-table v-loading="toolStore.loading" :data="group.tools" empty-text="暂无工具">
      <el-table-column prop="displayName" label="工具" min-width="150">
        <template #default="{ row }">
          <div class="tool-name-cell">
            <strong>{{ row.displayName }}</strong>
            <small>{{ row.toolKey }}</small>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="用途" min-width="260" show-overflow-tooltip />
      <el-table-column label="风险" width="100">
        <template #default="{ row }">
          <el-tag :type="riskType(row.riskLevel)" effect="plain">
            {{ riskText(row.riskLevel) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="110">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :loading="toolStore.isUpdating(row.id)"
            @change="toolStore.toggleEnabled(row.id, Boolean($event))"
          />
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>
