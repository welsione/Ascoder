import { request } from './httpClient'

export interface UserSummary {
  id: number
  username: string
  nickname: string | null
  email: string | null
  enabled: boolean
  accountNonLocked: boolean
  lastLoginAt: string | null
  createdAt: string
  roles: string[]
}

export interface UserDetail extends UserSummary {
  passwordChanged: boolean
}

export interface CreateUserRequest {
  username: string
  password: string
  nickname?: string
  email?: string
  roleCodes: string[]
}

export interface UpdateUserRequest {
  nickname?: string
  email?: string
  enabled?: boolean
}

export interface ResetPasswordRequest {
  newPassword: string
}

export interface AssignRolesRequest {
  roleCodes: string[]
}

export function listUsers() {
  return request<UserSummary[]>('/api/users')
}

export function getUser(id: number) {
  return request<UserDetail>(`/api/users/${id}`)
}

export function createUser(payload: CreateUserRequest) {
  return request<UserDetail>('/api/users', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function updateUser(id: number, payload: UpdateUserRequest) {
  return request<UserDetail>(`/api/users/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function resetUserPassword(id: number, payload: ResetPasswordRequest) {
  return request<void>(`/api/users/${id}/password`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function unlockUser(id: number) {
  return request<void>(`/api/users/${id}/unlock`, { method: 'PUT' })
}

export function assignUserRoles(id: number, payload: AssignRolesRequest) {
  return request<UserDetail>(`/api/users/${id}/roles`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function deleteUser(id: number) {
  return request<void>(`/api/users/${id}`, { method: 'DELETE' })
}
