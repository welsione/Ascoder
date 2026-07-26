export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  nickname?: string
}

export interface RefreshRequest {
  refreshToken: string
}

export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface UpdateProfileRequest {
  nickname?: string
  email?: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  mustChangePassword: boolean
  user: UserInfo
}

export interface UserInfo {
  id: number
  username: string
  nickname: string | null
  email: string | null
  roles: string[]
  permissions: string[]
}

export interface InitStatusResponse {
  initialized: boolean
}
