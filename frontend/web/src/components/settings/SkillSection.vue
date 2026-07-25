<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, RefreshCw, WandSparkles } from 'lucide-vue-next'
import { useSkillStore } from '../../stores/skill'
import { formatTime } from '../../utils/format'

const skillStore = useSkillStore()
const drawerVisible = ref(false)

function openCreate() {
  skillStore.resetForm()
  drawerVisible.value = true
}

function closeDrawer() {
  drawerVisible.value = false
}

async function createSkill() {
  const created = await skillStore.create()
  if (created) {
    drawerVisible.value = false
    ElMessage.success('Skill 已添加')
  }
}
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">Skill 列表</p>
        <h2>启停策略会直接改变首页聊天的回答风格</h2>
      </div>
      <div class="section-actions">
        <el-button
          circle
          :loading="skillStore.loading"
          title="刷新 Skill"
          aria-label="刷新 Skill"
          @click="skillStore.fetch"
        >
          <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
        </el-button>
        <el-button type="primary" @click="openCreate">
          <Plus class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          添加 Skill
        </el-button>
      </div>
    </div>

    <el-table v-loading="skillStore.loading" :data="skillStore.skills" empty-text="暂无 Skill">
      <el-table-column prop="name" label="名称" min-width="160" show-overflow-tooltip />
      <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
      <el-table-column prop="source" label="来源" width="120" />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" @change="skillStore.toggleEnabled(row.id, $event)" />
        </template>
      </el-table-column>
      <el-table-column label="更新时间" min-width="180">
        <template #default="{ row }">
          {{ formatTime(row.updatedAt) }}
        </template>
      </el-table-column>
    </el-table>

    <el-alert v-if="skillStore.error" type="error" :title="skillStore.error" show-icon :closable="false" />
  </section>

  <!-- 新增 Skill 抽屉 -->
  <el-drawer
    v-model="drawerVisible"
    title="添加 Skill"
    direction="rtl"
    size="480px"
    destroy-on-close
    @closed="skillStore.resetForm()"
  >
    <div class="drawer-form">
      <div class="settings-form-grid settings-form-grid-repo">
        <div>
          <label class="field-label">名称</label>
          <el-input v-model="skillStore.form.name" placeholder="例如 spring_boot_entry" maxlength="120" clearable show-word-limit />
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
        <div class="span-2">
          <label class="field-label">触发描述</label>
          <el-input
            v-model="skillStore.form.description"
            type="textarea"
            :rows="3"
            placeholder="说明什么时候应该使用这个 Skill"
          />
        </div>
        <div class="span-2">
          <label class="field-label">Skill 内容</label>
          <el-input v-model="skillStore.form.skillContent" type="textarea" :rows="8" placeholder="# Skill Instructions" />
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="closeDrawer">取消</el-button>
      <el-button type="primary" :loading="skillStore.createLoading" @click="createSkill">
        <WandSparkles class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        添加 Skill
      </el-button>
    </template>
  </el-drawer>
</template>
