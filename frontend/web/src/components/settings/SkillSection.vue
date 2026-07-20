<script setup lang="ts">
import { RefreshCw, WandSparkles } from 'lucide-vue-next'
import { useSkillStore } from '../../stores/skill'

const skillStore = useSkillStore()
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">新增 Skill</p>
        <h2>用团队规则扩展 Ascoder 的回答策略</h2>
      </div>
      <el-button
        circle
        :loading="skillStore.loading"
        title="刷新 Skill"
        aria-label="刷新 Skill"
        @click="skillStore.fetch"
      >
        <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
      </el-button>
    </div>

    <div class="settings-form-grid settings-form-grid-skill">
      <div>
        <label class="field-label">名称</label>
        <el-input v-model="skillStore.form.name" placeholder="例如 spring_boot_entry" clearable />
      </div>
      <div>
        <label class="field-label">来源</label>
        <el-input v-model="skillStore.form.source" placeholder="manual" clearable />
      </div>
      <div>
        <label class="field-label">启用</label>
        <div class="switch-wrap">
          <el-switch v-model="skillStore.form.enabled" />
        </div>
      </div>
      <div class="span-3">
        <label class="field-label">触发描述</label>
        <el-input
          v-model="skillStore.form.description"
          type="textarea"
          :rows="3"
          placeholder="说明什么时候应该使用这个 Skill"
        />
      </div>
      <div class="span-4">
        <label class="field-label">Skill 内容</label>
        <el-input v-model="skillStore.form.skillContent" type="textarea" :rows="8" placeholder="# Skill Instructions" />
      </div>
    </div>

    <div class="settings-actions">
      <el-button type="primary" :loading="skillStore.createLoading" @click="skillStore.create">
        <WandSparkles class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        添加 Skill
      </el-button>
    </div>

    <el-alert v-if="skillStore.error" type="error" :title="skillStore.error" show-icon :closable="false" />
  </section>

  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">Skill 列表</p>
        <h2>启停策略会直接改变首页聊天的回答风格</h2>
      </div>
    </div>

    <el-table v-loading="skillStore.loading" :data="skillStore.skills" empty-text="暂无 Skill">
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" width="120" />
      <el-table-column label="启用" width="120">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" @change="skillStore.toggleEnabled(row.id, $event)" />
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" min-width="180" />
    </el-table>
  </section>
</template>
