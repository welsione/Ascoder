<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { RefreshCw, UserPlus, Pencil, KeyRound, LockOpen, Users, Trash2 } from 'lucide-vue-next'
import { useUserStore } from '../../stores/user'
import { useAuthStore } from '../../stores/auth'
import * as userApi from '../../services/userApi'
import * as roleApi from '../../services/roleApi'
import { formatTime } from '../../utils/format'
import type { UserSummary } from '../../services/userApi'
import type { RoleSummary } from '../../services/roleApi'

const userStore = useUserStore()
const authStore = useAuthStore()

const roles = ref<RoleSummary[]>([])

onMounted(() => {
  userStore.fetchAll()
  loadRoles()
})

async function loadRoles() {
  try {
    roles.value = await roleApi.listRoles()
  } catch {
    // 静默失败，角色列表不影响用户展示
  }
}

const roleMap = computed(() => {
  const map = new Map<string, RoleSummary>()
  for (const r of roles.value) {
    map.set(r.code, r)
  }
  return map
})

function roleDisplayName(code: string): string {
  return roleMap.value.get(code)?.name ?? code
}

// ---- 新建用户对话框 ----
const createDialogVisible = ref(false)
const createForm = ref({
  username: '',
  password: '',
  nickname: '',
  email: '',
  roleCodes: [] as string[],
})
const createLoading = ref(false)

function openCreate() {
  createForm.value = { username: '', password: '', nickname: '', email: '', roleCodes: [] }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.username || !createForm.value.password) {
    ElMessage.warning('请填写用户名和密码')
    return
  }
  createLoading.value = true
  try {
    await userApi.createUser({
      username: createForm.value.username,
      password: createForm.value.password,
      nickname: createForm.value.nickname || undefined,
      email: createForm.value.email || undefined,
      roleCodes: createForm.value.roleCodes,
    })
    ElMessage.success('用户已创建')
    createDialogVisible.value = false
    await userStore.fetchAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '创建用户失败')
  } finally {
    createLoading.value = false
  }
}

// ---- 编辑用户对话框 ----
const editDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editForm = ref({ nickname: '', email: '', enabled: true })
const editLoading = ref(false)

function openEdit(user: UserSummary) {
  editingId.value = user.id
  editForm.value = {
    nickname: user.nickname ?? '',
    email: user.email ?? '',
    enabled: user.enabled,
  }
  editDialogVisible.value = true
}

async function handleEdit() {
  if (editingId.value === null) return
  editLoading.value = true
  try {
    await userApi.updateUser(editingId.value, {
      nickname: editForm.value.nickname || undefined,
      email: editForm.value.email || undefined,
      enabled: editForm.value.enabled,
    })
    ElMessage.success('用户已更新')
    editDialogVisible.value = false
    await userStore.fetchAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '更新用户失败')
  } finally {
    editLoading.value = false
  }
}

// ---- 重置密码对话框 ----
const passwordDialogVisible = ref(false)
const passwordUserId = ref<number | null>(null)
const passwordForm = ref({ newPassword: '' })
const passwordLoading = ref(false)

function openResetPassword(user: UserSummary) {
  passwordUserId.value = user.id
  passwordForm.value = { newPassword: '' }
  passwordDialogVisible.value = true
}

async function handleResetPassword() {
  if (passwordUserId.value === null) return
  if (!passwordForm.value.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  passwordLoading.value = true
  try {
    await userApi.resetUserPassword(passwordUserId.value, { newPassword: passwordForm.value.newPassword })
    ElMessage.success('密码已重置')
    passwordDialogVisible.value = false
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '重置密码失败')
  } finally {
    passwordLoading.value = false
  }
}

// ---- 分配角色对话框 ----
const rolesDialogVisible = ref(false)
const rolesUserId = ref<number | null>(null)
const rolesForm = ref<{ roleCodes: string[] }>({ roleCodes: [] })
const rolesLoading = ref(false)

function openAssignRoles(user: UserSummary) {
  rolesUserId.value = user.id
  rolesForm.value = { roleCodes: [...user.roles] }
  rolesDialogVisible.value = true
}

async function handleAssignRoles() {
  if (rolesUserId.value === null) return
  rolesLoading.value = true
  try {
    await userApi.assignUserRoles(rolesUserId.value, { roleCodes: rolesForm.value.roleCodes })
    ElMessage.success('角色已分配')
    rolesDialogVisible.value = false
    await userStore.fetchAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '分配角色失败')
  } finally {
    rolesLoading.value = false
  }
}

// ---- 解锁用户 ----
const unlockingId = ref<number | null>(null)

async function handleUnlock(user: UserSummary) {
  unlockingId.value = user.id
  try {
    await userApi.unlockUser(user.id)
    ElMessage.success('用户已解锁')
    await userStore.fetchAll()
  } catch (err) {
    ElMessage.error(err instanceof Error ? err.message : '解锁失败')
  } finally {
    unlockingId.value = null
  }
}

// ---- 删除用户 ----
async function handleDelete(user: UserSummary) {
  if (user.username === 'admin') {
    ElMessage.warning('admin 用户不可删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除用户「${user.username}」？此操作不可恢复。`, '删除确认', { type: 'warning' })
    await userApi.deleteUser(user.id)
    ElMessage.success('用户已删除')
    await userStore.fetchAll()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err instanceof Error ? err.message : '删除用户失败')
    }
  }
}

const enabledRoleOptions = computed(() => roles.value.filter((r) => r.enabled))
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">用户管理</p>
        <h2>管理平台用户账号、角色分配与登录状态</h2>
      </div>
      <div class="section-actions">
        <el-button circle :loading="userStore.loading" title="刷新" aria-label="刷新" @click="userStore.fetchAll()">
          <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
        </el-button>
        <el-button type="primary" @click="openCreate">
          <UserPlus class="button-icon" :size="16" :stroke-width="1.8" />
          新增用户
        </el-button>
      </div>
    </div>

    <el-table v-loading="userStore.loading" :data="userStore.users" empty-text="暂无用户">
      <el-table-column prop="username" label="用户名" min-width="120" show-overflow-tooltip />
      <el-table-column label="昵称" min-width="120">
        <template #default="{ row }">
          {{ row.nickname || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="邮箱" min-width="180">
        <template #default="{ row }">
          {{ row.email || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="160">
        <template #default="{ row }">
          <el-tag v-for="code in row.roles" :key="code" size="small" class="cell-tag">
            {{ roleDisplayName(code) }}
          </el-tag>
          <span v-if="!row.roles.length" class="cell-sub">--</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="!row.enabled" size="small" type="info">已禁用</el-tag>
          <el-tag v-else-if="!row.accountNonLocked" size="small" type="danger">已锁定</el-tag>
          <el-tag v-else size="small" type="success">正常</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="最后登录" min-width="170">
        <template #default="{ row }">
          {{ formatTime(row.lastLoginAt) }}
        </template>
      </el-table-column>
      <el-table-column label="创建时间" min-width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-tooltip content="编辑" placement="top" :show-after="300">
              <el-button size="small" circle aria-label="编辑" @click="openEdit(row)">
                <Pencil aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip content="重置密码" placement="top" :show-after="300">
              <el-button size="small" circle aria-label="重置密码" @click="openResetPassword(row)">
                <KeyRound aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip v-if="!row.accountNonLocked" content="解锁" placement="top" :show-after="300">
              <el-button size="small" circle :loading="unlockingId === row.id" aria-label="解锁" @click="handleUnlock(row)">
                <LockOpen aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip content="分配角色" placement="top" :show-after="300">
              <el-button size="small" circle aria-label="分配角色" @click="openAssignRoles(row)">
                <Users aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top" :show-after="300">
              <el-button size="small" circle type="danger" plain :disabled="row.username === 'admin'" aria-label="删除" @click="handleDelete(row)">
                <Trash2 aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-alert v-if="userStore.error" type="error" :title="userStore.error" show-icon :closable="false" />
  </section>

  <!-- 新建用户对话框 -->
  <el-dialog v-model="createDialogVisible" title="新增用户" width="480px" destroy-on-close>
    <div class="drawer-form">
      <div class="settings-form-grid settings-form-grid-repo">
        <div>
          <label class="field-label">用户名</label>
          <el-input v-model="createForm.username" placeholder="输入用户名" maxlength="50" clearable show-word-limit />
        </div>
        <div>
          <label class="field-label">密码</label>
          <el-input v-model="createForm.password" type="password" show-password placeholder="输入初始密码" />
        </div>
        <div>
          <label class="field-label">昵称</label>
          <el-input v-model="createForm.nickname" placeholder="可选" clearable />
        </div>
        <div>
          <label class="field-label">邮箱</label>
          <el-input v-model="createForm.email" placeholder="可选" clearable />
        </div>
        <div class="span-2">
          <label class="field-label">角色</label>
          <el-select v-model="createForm.roleCodes" multiple filterable collapse-tags placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in enabledRoleOptions" :key="r.code" :label="`${r.name} (${r.code})`" :value="r.code" />
          </el-select>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="createDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="createLoading" @click="handleCreate">创建</el-button>
    </template>
  </el-dialog>

  <!-- 编辑用户对话框 -->
  <el-dialog v-model="editDialogVisible" title="编辑用户" width="480px" destroy-on-close>
    <div class="drawer-form">
      <div class="settings-form-grid settings-form-grid-repo">
        <div>
          <label class="field-label">昵称</label>
          <el-input v-model="editForm.nickname" placeholder="可选" clearable />
        </div>
        <div>
          <label class="field-label">邮箱</label>
          <el-input v-model="editForm.email" placeholder="可选" clearable />
        </div>
        <div>
          <label class="field-label">启用</label>
          <div class="switch-wrap"><el-switch v-model="editForm.enabled" /></div>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="editLoading" @click="handleEdit">保存</el-button>
    </template>
  </el-dialog>

  <!-- 重置密码对话框 -->
  <el-dialog v-model="passwordDialogVisible" title="重置密码" width="440px" destroy-on-close>
    <div class="drawer-form">
      <div class="settings-form-grid settings-form-grid-repo">
        <div class="span-2">
          <label class="field-label">新密码</label>
          <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="输入新密码" />
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="passwordDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="passwordLoading" @click="handleResetPassword">确认重置</el-button>
    </template>
  </el-dialog>

  <!-- 分配角色对话框 -->
  <el-dialog v-model="rolesDialogVisible" title="分配角色" width="480px" destroy-on-close>
    <div class="drawer-form">
      <div class="settings-form-grid settings-form-grid-repo">
        <div class="span-2">
          <label class="field-label">角色</label>
          <el-select v-model="rolesForm.roleCodes" multiple filterable collapse-tags placeholder="选择角色" style="width: 100%">
            <el-option v-for="r in enabledRoleOptions" :key="r.code" :label="`${r.name} (${r.code})`" :value="r.code" />
          </el-select>
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="rolesDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="rolesLoading" @click="handleAssignRoles">保存</el-button>
    </template>
  </el-dialog>
</template>
