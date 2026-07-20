<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { GitBranch, KeyRound, Plus, RefreshCw } from 'lucide-vue-next'
import { useRepositoryStore } from '../../stores/repository'
import type { CodeRepository } from '../../types/repository'

const repositoryStore = useRepositoryStore()
const sourceMode = ref<'remote' | 'local'>('remote')
const showCreateRepository = ref(false)

// 凭据编辑对话框状态
const credentialDialogVisible = ref(false)
const credentialRepository = ref<CodeRepository | null>(null)
const credentialUsername = ref('')
const credentialPassword = ref('')

function repositoryBranchCount(repositoryId: number) {
  return repositoryStore.branchesByRepository[repositoryId]?.filter((branch) => branch.active).length ?? null
}

function sourceLabel(repository: CodeRepository) {
  return repository.remoteUrl ? '远程仓库' : '本地仓库'
}

function sourceType(repository: CodeRepository) {
  return repository.remoteUrl ? 'primary' : 'info'
}

function displayLocation(repository: CodeRepository) {
  const value = repository.remoteUrl || repository.localPath
  if (!value) return '未配置路径'
  const parts = value.split('/').filter(Boolean)
  return parts.length >= 2 ? parts.slice(-2).join('/') : value
}

function formatTime(value?: string | null) {
  if (!value) return '未同步'
  try {
    const date = new Date(value)
    const pad = (n: number) => String(n).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  } catch {
    return value
  }
}

async function createRepository() {
  if (sourceMode.value === 'local') {
    repositoryStore.form.remoteUrl = ''
    repositoryStore.form.authUsername = ''
    repositoryStore.form.authPassword = ''
  }
  const created = await repositoryStore.create()
  if (created) {
    showCreateRepository.value = false
    ElMessage.success('仓库已添加')
  }
}

async function refreshRepositoryBranches(repoId: number) {
  const branches = await repositoryStore.refreshBranches(repoId)
  if (branches.length > 0) {
    ElMessage.success('分支发现已刷新')
  }
}

function openCredentialDialog(repository: CodeRepository) {
  credentialRepository.value = repository
  credentialUsername.value = repository.authUsername ?? ''
  credentialPassword.value = ''
  credentialDialogVisible.value = true
}

async function saveCredentials() {
  if (!credentialRepository.value) return
  const updated = await repositoryStore.updateCredentials(
    credentialRepository.value.id,
    credentialUsername.value,
    credentialPassword.value
  )
  if (updated) {
    credentialDialogVisible.value = false
    ElMessage.success('凭据已更新')
  }
}
</script>

<template>
  <section class="repository-flow surface-panel">
    <span><strong>接入仓库</strong></span>
    <span class="flow-divider" aria-hidden="true" />
    <span>分支会出现在<strong>项目空间</strong>创建流程中</span>
  </section>

  <section class="surface-panel settings-block">
    <div class="section-heading repository-card-heading">
      <div>
        <p class="kicker">仓库列表</p>
        <h2>同步状态</h2>
      </div>
      <el-button
        circle
        :loading="repositoryStore.loading"
        title="刷新仓库"
        aria-label="刷新仓库"
        @click="repositoryStore.fetch"
      >
        <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
      </el-button>
    </div>

    <el-alert v-if="repositoryStore.error" type="error" :title="repositoryStore.error" show-icon :closable="false" />

    <el-table
      class="repository-table"
      v-loading="repositoryStore.loading"
      :data="repositoryStore.repositories"
      empty-text="暂无仓库"
    >
      <el-table-column label="仓库" min-width="180">
        <template #default="{ row }">
          <div class="repo-cell">
            <strong>{{ row.name }}</strong>
            <span>{{ row.defaultBranch || '未设置默认分支' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="来源与路径" min-width="320">
        <template #default="{ row }">
          <div class="repo-location">
            <el-tag :type="sourceType(row)" size="small">{{ sourceLabel(row) }}</el-tag>
            <el-tooltip :content="row.remoteUrl || row.localPath" placement="top" :show-after="300">
              <span>{{ displayLocation(row) }}</span>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="112">
        <template #default="{ row }">
          <el-tag :type="repositoryStore.statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分支" width="104">
        <template #default="{ row }">
          {{ repositoryBranchCount(row.id) ?? '未加载' }}
        </template>
      </el-table-column>
      <el-table-column prop="lastPulledAt" label="最近同步" min-width="150">
        <template #default="{ row }">
          {{ formatTime(row.lastPulledAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="168" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-tooltip content="同步代码" placement="top" :show-after="300">
              <el-button
                size="small"
                circle
                :disabled="!row.remoteUrl"
                :loading="repositoryStore.syncingId === row.id"
                aria-label="同步代码"
                @click="repositoryStore.pullRemote(row.id)"
              >
                <RefreshCw aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip content="刷新分支" placement="top" :show-after="300">
              <el-button
                size="small"
                circle
                :loading="repositoryStore.branchRefreshingId === row.id"
                aria-label="刷新分支"
                @click="refreshRepositoryBranches(row.id)"
              >
                <GitBranch aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip :content="row.hasCredentials ? '更新凭据' : '配置凭据'" placement="top" :show-after="300">
              <el-button
                size="small"
                circle
                :type="row.hasCredentials ? 'success' : 'warning'"
                :disabled="!row.remoteUrl"
                aria-label="配置凭据"
                @click="openCredentialDialog(row)"
              >
                <KeyRound aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </section>

  <section class="surface-panel settings-block repository-create-section">
    <div class="section-heading repository-card-heading">
      <div>
        <p class="kicker">新增仓库</p>
        <h2>添加一个代码仓库</h2>
      </div>
      <div class="section-actions">
        <el-button
          type="primary"
          :plain="showCreateRepository"
          :aria-expanded="showCreateRepository"
          aria-controls="repository-create-panel"
          @click="showCreateRepository = !showCreateRepository"
        >
          <Plus class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
          {{ showCreateRepository ? '收起' : '添加仓库' }}
        </el-button>
      </div>
    </div>

    <el-collapse-transition>
      <div v-if="showCreateRepository" id="repository-create-panel" class="repository-create-panel">
        <div class="source-switch" aria-label="仓库来源">
          <el-segmented
            v-model="sourceMode"
            :options="[
              { label: '远程仓库', value: 'remote' },
              { label: '本地仓库', value: 'local' },
            ]"
          />
        </div>

        <div class="settings-form-grid settings-form-grid-repo repository-form-grid">
          <div>
            <label class="field-label">仓库名称</label>
            <el-input v-model="repositoryStore.form.name" placeholder="例如 ascoder" maxlength="120" clearable />
          </div>
          <div>
            <label class="field-label">默认分支</label>
            <el-input v-model="repositoryStore.form.defaultBranch" placeholder="例如 main / develop" maxlength="255" clearable />
          </div>
          <div class="span-2">
            <label class="field-label">远程 Git 地址</label>
            <el-input
              v-model="repositoryStore.form.remoteUrl"
              placeholder="例如 https://github.com/org/repo.git；填写后会 clone 到 REPO_ROOT"
              :disabled="sourceMode === 'local'"
              clearable
            />
          </div>
          <div class="span-2">
            <label class="field-label">本地路径</label>
            <el-input
              v-model="repositoryStore.form.localPath"
              :placeholder="sourceMode === 'remote' ? '远程仓库 clone 目标路径；为空时使用仓库名称' : '本地仓库路径，必须位于 REPO_ROOT 下'"
              clearable
            />
          </div>
          <template v-if="sourceMode === 'remote'">
            <div>
              <label class="field-label">认证用户名</label>
              <el-input
                v-model="repositoryStore.form.authUsername"
                placeholder="Git HTTPS 认证用户名（可选）"
                maxlength="255"
                clearable
              />
            </div>
            <div>
              <label class="field-label">认证密码</label>
              <el-input
                v-model="repositoryStore.form.authPassword"
                type="password"
                placeholder="Git HTTPS 认证密码（可选）"
                show-password
                clearable
              />
            </div>
          </template>
          <div class="form-submit">
            <el-button type="primary" :loading="repositoryStore.createLoading" @click="createRepository">
              <Plus class="button-icon" aria-hidden="true" :size="16" :stroke-width="1.8" />
              添加仓库
            </el-button>
          </div>
        </div>
      </div>
    </el-collapse-transition>
  </section>

  <el-dialog
    v-model="credentialDialogVisible"
    :title="credentialRepository?.hasCredentials ? '更新凭据' : '配置凭据'"
    width="440px"
    destroy-on-close
  >
    <p class="credential-dialog-hint">
      为 <strong>{{ credentialRepository?.name }}</strong> 配置 Git HTTPS 认证凭据。
      配置后，clone / fetch / pull 操作将自动使用该凭据。
    </p>
    <div class="credential-dialog-form">
      <div>
        <label class="field-label">认证用户名</label>
        <el-input v-model="credentialUsername" placeholder="Git HTTPS 认证用户名" maxlength="255" clearable />
      </div>
      <div>
        <label class="field-label">认证密码</label>
        <el-input
          v-model="credentialPassword"
          type="password"
          :placeholder="credentialRepository?.hasCredentials ? '留空则保持原密码不变' : 'Git HTTPS 认证密码'"
          show-password
          clearable
        />
      </div>
    </div>
    <template #footer>
      <el-button @click="credentialDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="saveCredentials">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.repository-flow {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--spacing-3);
  padding: var(--spacing-3) var(--spacing-4);
  color: var(--muted);
  font-size: var(--font-size-sm);
}

.repository-flow strong {
  color: var(--text);
}

.flow-divider {
  width: 28px;
  height: 1px;
  background: var(--stroke);
}

.repository-card-heading h2 {
  font-size: var(--font-size-xl);
}

.repository-create-panel {
  display: grid;
  gap: var(--spacing-4);
}

.source-switch {
  margin-top: calc(-1 * var(--spacing-1));
}

.repository-form-grid {
  align-items: end;
}

.form-submit {
  grid-column: span 2;
  display: flex;
  justify-content: flex-end;
}

.repo-cell,
.repo-location {
  min-width: 0;
}

.repo-cell {
  display: grid;
  gap: 2px;
}

.repo-cell strong,
.repo-location span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.repo-cell span {
  color: var(--muted);
  font-size: var(--font-size-xs);
}

.repo-location {
  display: flex;
  align-items: center;
  gap: var(--spacing-2);
}

.repo-location span {
  color: var(--subtle);
}

.credential-dialog-hint {
  margin: 0 0 var(--spacing-4);
  color: var(--muted);
  font-size: var(--font-size-sm);
  line-height: var(--line-height-normal);
}

.credential-dialog-form {
  display: grid;
  gap: var(--spacing-3);
}

@media (max-width: 900px) {
  .repository-flow,
  .form-submit {
    justify-content: flex-start;
  }

  .repository-form-grid {
    grid-template-columns: 1fr;
  }

  .repository-form-grid .span-2,
  .form-submit {
    grid-column: span 1;
  }
}
</style>
