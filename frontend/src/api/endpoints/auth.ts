import { apiClient } from '@/api/client'
import type { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '@/api/types'

export const authApi = {
  register: (request: RegisterRequest) =>
    apiClient.post<AuthResponse>('/api/auth/register', request),
  login: (request: LoginRequest) => apiClient.post<AuthResponse>('/api/auth/login', request),
  me: () => apiClient.get<UserResponse>('/api/me'),
}
