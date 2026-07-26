import { request } from './httpClient'

export interface RoleSummary {
  id: number
  code: string
  name: string
  description: string | null
  builtin: boolean
  enabled: boolean
  permissionCount: number
}

export interface RoleDetail extends RoleSummary {
  permissions: string[]
}

export interface CreateRoleRequest {
  code: string
  name: string
  description?: string
}

export interface UpdateRoleRequest {
  name?: string
  description?: string
  enabled?: boolean
}

export interface AssignPermissionsRequest {
  permissionCodes: string[]
}

export interface PermissionSummary {
  id: number
  code: string
  name: string
  module: string
  description: string | null
}

export function listRoles() {
  return request<RoleSummary[]>('/api/roles')
}

export function getRole(id: number) {
  return request<RoleDetail>(`/api/roles/${id}`)
}

export function createRole(payload: CreateRoleRequest) {
  return request<RoleDetail>('/api/roles', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateRole(id: number, payload: UpdateRoleRequest) {
  return request<RoleDetail>(`/api/roles/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function deleteRole(id: number) {
  return request<void>(`/api/roles/${id}`, { method: 'DELETE' })
}

export function assignRolePermissions(id: number, payload: AssignPermissionsRequest) {
  return request<RoleDetail>(`/api/roles/${id}/permissions`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function listPermissions() {
  return request<PermissionSummary[]>('/api/permissions')
}
