import { request } from './httpClient'
import type { LoginRequest, RegisterRequest, RefreshRequest, ChangePasswordRequest, AuthResponse, InitStatusResponse, UserInfo } from '../types/auth'

export function getInitStatus() {
  return request<InitStatusResponse>('/api/auth/init-status')
}

export function register(payload: RegisterRequest) {
  return request<AuthResponse>('/api/auth/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function login(payload: LoginRequest) {
  return request<AuthResponse>('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function refresh(payload: RefreshRequest) {
  return request<AuthResponse>('/api/auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function logout(payload: RefreshRequest) {
  return request<void>('/api/auth/logout', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export function getCurrentUser() {
  return request<UserInfo>('/api/auth/me')
}

export function changePassword(payload: ChangePasswordRequest) {
  return request<void>('/api/auth/change-password', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}
