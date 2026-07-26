<script setup lang="ts">
import { ref } from 'vue'
import { Plus, RefreshCw, ServerCog } from 'lucide-vue-next'
import { useMcpServerStore } from '../../stores/mcpServer'
import { useNotify } from '../../composables/useNotify'

const mcpStore = useMcpServerStore()
const notify = useNotify()
const drawerVisible = ref(false)

function openCreate() {
  mcpStore.resetForm()
  drawerVisible.value = true
}

function closeDrawer() {
  drawerVisible.value = false
}

async function createMcp() {
  const created = await mcpStore.create()
  if (created) {
    drawerVisible.value = false
    notify.success('MCP Server 已添加')
  }
}
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">MCP 列表</p>
        <h2>哪些工具能被代理调用，在这里统一看清</h2>
      </div>
      <div class="section-actions">
        <el-button
          circle
          :loading="mcpStore.loading"
          title="刷新 MCP"
          aria-label="刷新 MCP"
          @click="mcpStore.fetch"
        >
          <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
        </el-button>
        <el-button type="primary" @click="openCreate">
          <Plus class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          添加 MCP Server
        </el-button>
      </div>
    </div>

    <el-table v-loading="mcpStore.loading" :data="mcpStore.servers" empty-text="暂无 MCP Server">
      <el-table-column prop="name" label="名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="transport" label="Transport" width="110" />
      <el-table-column prop="command" label="Command" min-width="160" show-overflow-tooltip />
      <el-table-column prop="endpointUrl" label="Endpoint" min-width="220" show-overflow-tooltip />
      <el-table-column label="启用" width="80">
        <template #default="{ row }">
          <el-switch :model-value="row.enabled" @change="mcpStore.toggleEnabled(row.id, $event)" />
        </template>
      </el-table-column>
      <el-table-column prop="lastError" label="最近错误" min-width="220" show-overflow-tooltip />
    </el-table>

    <el-alert v-if="mcpStore.error" type="error" :title="mcpStore.error" show-icon :closable="false" />
  </section>

  <!-- 新增 MCP Server 抽屉 -->
  <el-drawer
    v-model="drawerVisible"
    title="添加 MCP Server"
    direction="rtl"
    size="640px"
    destroy-on-close
    @closed="mcpStore.resetForm()"
  >
    <div class="drawer-form">
      <p class="field-section-title">基础信息</p>
      <div class="settings-form-grid settings-form-grid-repo">
        <div>
          <label class="field-label">名称</label>
          <el-input v-model="mcpStore.form.name" placeholder="例如 filesystem" maxlength="120" clearable show-word-limit />
        </div>
        <div>
          <label class="field-label">Transport</label>
          <el-select v-model="mcpStore.form.transport" placeholder="选择传输方式">
            <el-option label="STDIO" value="STDIO" />
            <el-option label="SSE" value="SSE" />
            <el-option label="HTTP" value="HTTP" />
          </el-select>
        </div>
        <div>
          <label class="field-label">启用</label>
          <div class="switch-wrap">
            <el-switch v-model="mcpStore.form.enabled" />
          </div>
        </div>
        <div>
          <label class="field-label">超时秒数</label>
          <el-input-number v-model="mcpStore.form.timeoutSeconds" :min="1" :max="300" />
        </div>
        <div class="span-2">
          <label class="field-label">描述</label>
          <el-input v-model="mcpStore.form.description" type="textarea" :rows="2" placeholder="说明该 MCP Server 提供的能力" />
        </div>
      </div>

      <p class="field-section-title">传输配置</p>
      <div class="settings-form-grid settings-form-grid-repo">
        <div class="span-2">
          <label class="field-label">Command</label>
          <el-input v-model="mcpStore.form.command" placeholder="STDIO 使用，例如 npx" clearable />
        </div>
        <div class="span-2">
          <label class="field-label">Endpoint URL</label>
          <el-input v-model="mcpStore.form.endpointUrl" placeholder="HTTP/SSE 使用，例如 http://localhost:3000/sse" clearable />
        </div>
        <div class="span-2">
          <label class="field-label">Arguments JSON</label>
          <el-input v-model="mcpStore.form.argumentsJson" type="textarea" :rows="2" placeholder='["-y"]' />
        </div>
        <div class="span-2">
          <label class="field-label">Headers JSON</label>
          <el-input v-model="mcpStore.form.headersJson" type="textarea" :rows="2" placeholder='{"Authorization":"Bearer ..."}' />
        </div>
        <div class="span-2">
          <label class="field-label">Query Params JSON</label>
          <el-input v-model="mcpStore.form.queryParamsJson" type="textarea" :rows="2" placeholder="{}" />
        </div>
      </div>

      <p class="field-section-title">工具过滤</p>
      <div class="settings-form-grid settings-form-grid-repo">
        <div class="span-2">
          <label class="field-label">Enabled Tools JSON</label>
          <el-input v-model="mcpStore.form.enabledToolsJson" type="textarea" :rows="2" placeholder='["read_file"]' />
        </div>
        <div class="span-2">
          <label class="field-label">Disabled Tools JSON</label>
          <el-input v-model="mcpStore.form.disabledToolsJson" type="textarea" :rows="2" placeholder='["delete_file"]' />
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="closeDrawer">取消</el-button>
      <el-button type="primary" :loading="mcpStore.createLoading" @click="createMcp">
        <ServerCog class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
        添加
      </el-button>
    </template>
  </el-drawer>
</template>
