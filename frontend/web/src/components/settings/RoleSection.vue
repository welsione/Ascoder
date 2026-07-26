<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { RefreshCw, ShieldPlus, Pencil, KeyRound, Trash2 } from 'lucide-vue-next'
import { useNotify } from '../../composables/useNotify'
import * as roleApi from '../../services/roleApi'
import { formatTime } from '../../utils/format'
import type { RoleSummary, PermissionSummary } from '../../services/roleApi'

const notify = useNotify()
const roles = ref<RoleSummary[]>([])
const permissions = ref<PermissionSummary[]>([])
const loading = ref(false)
const error = ref('')

onMounted(() => {
  fetchAll()
})

async function fetchAll() {
  loading.value = true
  error.value = ''
  try {
    const [roleList, permList] = await Promise.all([roleApi.listRoles(), roleApi.listPermissions()])
    roles.value = roleList
    permissions.value = permList
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载角色列表失败'
  } finally {
    loading.value = false
  }
}

// 按模块分组的权限列表
const permissionsByModule = computed(() => {
  const groups = new Map<string, PermissionSummary[]>()
  for (const p of permissions.value) {
    const list = groups.get(p.module) ?? []
    list.push(p)
    groups.set(p.module, list)
  }
  return Array.from(groups.entries()).map(([module, perms]) => ({ module, perms }))
})

// ---- 新建角色对话框 ----
const createDialogVisible = ref(false)
const createForm = ref({ code: '', name: '', description: '' })
const createLoading = ref(false)

function openCreate() {
  createForm.value = { code: '', name: '', description: '' }
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createForm.value.code || !createForm.value.name) {
    notify.warning('请填写角色编码和名称')
    return
  }
  createLoading.value = true
  try {
    await roleApi.createRole({
      code: createForm.value.code,
      name: createForm.value.name,
      description: createForm.value.description || undefined,
    })
    notify.success('角色已创建')
    createDialogVisible.value = false
    await fetchAll()
  } catch (err) {
    notify.error(err, '创建角色失败')
  } finally {
    createLoading.value = false
  }
}

// ---- 编辑角色对话框 ----
const editDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const editingBuiltin = ref(false)
const editForm = ref({ name: '', description: '', enabled: true })
const editLoading = ref(false)

function openEdit(role: RoleSummary) {
  editingId.value = role.id
  editingBuiltin.value = role.builtin
  editForm.value = {
    name: role.name,
    description: role.description ?? '',
    enabled: role.enabled,
  }
  editDialogVisible.value = true
}

async function handleEdit() {
  if (editingId.value === null) return
  editLoading.value = true
  try {
    const payload: roleApi.UpdateRoleRequest = {}
    if (!editingBuiltin.value) {
      payload.name = editForm.value.name
      payload.enabled = editForm.value.enabled
    }
    payload.description = editForm.value.description || undefined
    await roleApi.updateRole(editingId.value, payload)
    notify.success('角色已更新')
    editDialogVisible.value = false
    await fetchAll()
  } catch (err) {
    notify.error(err, '更新角色失败')
  } finally {
    editLoading.value = false
  }
}

// ---- 分配权限对话框 ----
const permDialogVisible = ref(false)
const permRoleId = ref<number | null>(null)
const permRoleName = ref('')
const selectedPermissions = ref<string[]>([])
const permLoading = ref(false)

async function openAssignPermissions(role: RoleSummary) {
  permRoleId.value = role.id
  permRoleName.value = role.name
  permLoading.value = true
  permDialogVisible.value = true
  try {
    const detail = await roleApi.getRole(role.id)
    selectedPermissions.value = [...detail.permissions]
  } catch (err) {
    notify.error(err, '加载权限失败')
  } finally {
    permLoading.value = false
  }
}

async function handleAssignPermissions() {
  if (permRoleId.value === null) return
  permLoading.value = true
  try {
    await roleApi.assignRolePermissions(permRoleId.value, { permissionCodes: selectedPermissions.value })
    notify.success('权限已更新')
    permDialogVisible.value = false
    await fetchAll()
  } catch (err) {
    notify.error(err, '分配权限失败')
  } finally {
    permLoading.value = false
  }
}

// ---- 删除角色 ----
async function handleDelete(role: RoleSummary) {
  if (role.builtin) {
    notify.warning('内置角色不可删除')
    return
  }
  const ok = await notify.confirm(`确认删除角色「${role.name}」？此操作不可恢复。`, '删除确认')
  if (!ok) return
  try {
    await roleApi.deleteRole(role.id)
    notify.success('角色已删除')
    await fetchAll()
  } catch (err) {
    notify.error(err, '删除角色失败')
  }
}
</script>

<template>
  <section class="surface-panel settings-block">
    <div class="section-heading">
      <div>
        <p class="kicker">角色权限</p>
        <h2>管理角色定义与权限分配，控制用户可访问的功能范围</h2>
      </div>
      <div class="section-actions">
        <el-button circle :loading="loading" title="刷新" aria-label="刷新" @click="fetchAll">
          <RefreshCw aria-hidden="true" :size="16" :stroke-width="1.8" />
        </el-button>
        <el-button type="primary" @click="openCreate">
          <ShieldPlus class="button-icon" :size="16" :stroke-width="1.8" />
          新增角色
        </el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="roles" empty-text="暂无角色">
      <el-table-column prop="code" label="编码" min-width="120" show-overflow-tooltip />
      <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="描述" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.description || '--' }}
        </template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.builtin" size="small" type="info">内置</el-tag>
          <el-tag v-else size="small">自定义</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.enabled" size="small" type="success">启用</el-tag>
          <el-tag v-else size="small" type="info">禁用</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="permissionCount" label="权限数" width="80" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <div class="table-actions">
            <el-tooltip content="编辑" placement="top" :show-after="300">
              <el-button size="small" circle aria-label="编辑" @click="openEdit(row)">
                <Pencil aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip content="分配权限" placement="top" :show-after="300">
              <el-button size="small" circle aria-label="分配权限" @click="openAssignPermissions(row)">
                <KeyRound aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top" :show-after="300">
              <el-button size="small" circle type="danger" plain :disabled="row.builtin" aria-label="删除" @click="handleDelete(row)">
                <Trash2 aria-hidden="true" :size="15" :stroke-width="1.8" />
              </el-button>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-alert v-if="error" type="error" :title="error" show-icon :closable="false" />
  </section>

  <!-- 新建角色对话框 -->
  <el-dialog v-model="createDialogVisible" title="新增角色" width="480px" destroy-on-close>
    <div class="drawer-form">
      <div class="settings-form-grid settings-form-grid-repo">
        <div>
          <label class="field-label">角色编码</label>
          <el-input v-model="createForm.code" placeholder="例如 PROJECT_MANAGER" maxlength="50" clearable show-word-limit />
        </div>
        <div>
          <label class="field-label">角色名称</label>
          <el-input v-model="createForm.name" placeholder="例如 项目管理员" maxlength="120" clearable show-word-limit />
        </div>
        <div class="span-2">
          <label class="field-label">描述</label>
          <el-input v-model="createForm.description" type="textarea" :rows="2" placeholder="可选" />
        </div>
      </div>
    </div>
    <template #footer>
      <el-button @click="createDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="createLoading" @click="handleCreate">创建</el-button>
    </template>
  </el-dialog>

  <!-- 编辑角色对话框 -->
  <el-dialog v-model="editDialogVisible" title="编辑角色" width="480px" destroy-on-close>
    <div class="drawer-form">
      <div class="settings-form-grid settings-form-grid-repo">
        <div>
          <label class="field-label">角色名称</label>
          <el-input v-model="editForm.name" :disabled="editingBuiltin" placeholder="角色名称" clearable />
        </div>
        <div>
          <label class="field-label">启用</label>
          <div class="switch-wrap"><el-switch v-model="editForm.enabled" :disabled="editingBuiltin" /></div>
        </div>
        <div class="span-2">
          <label class="field-label">描述</label>
          <el-input v-model="editForm.description" type="textarea" :rows="2" placeholder="可选" />
        </div>
      </div>
      <el-alert v-if="editingBuiltin" type="info" :closable="false" show-icon>
        内置角色仅可修改描述，名称和启用状态不可更改。
      </el-alert>
    </div>
    <template #footer>
      <el-button @click="editDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="editLoading" @click="handleEdit">保存</el-button>
    </template>
  </el-dialog>

  <!-- 分配权限对话框 -->
  <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${permRoleName}`" width="600px" destroy-on-close>
    <div v-loading="permLoading" class="drawer-form">
      <div v-for="group in permissionsByModule" :key="group.module" class="perm-group">
        <p class="field-section-title">{{ group.module }}</p>
        <el-checkbox-group v-model="selectedPermissions">
          <div class="perm-checkbox-list">
            <el-checkbox v-for="p in group.perms" :key="p.code" :value="p.code" :label="p.code">
              <span class="perm-code">{{ p.name }}</span>
              <small class="perm-desc">{{ p.code }}</small>
            </el-checkbox>
          </div>
        </el-checkbox-group>
      </div>
      <el-alert v-if="permissionsByModule.length === 0 && !permLoading" type="info" :closable="false" show-icon>
        暂无可用权限。
      </el-alert>
    </div>
    <template #footer>
      <el-button @click="permDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="permLoading" @click="handleAssignPermissions">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.perm-group {
  margin-bottom: 16px;
}

.perm-checkbox-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.perm-code {
  font-weight: 500;
}

.perm-desc {
  margin-left: 8px;
  color: var(--text-tertiary, #999);
  font-size: 12px;
}
</style>
