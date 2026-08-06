import { request } from './client'
import { clearStoredAuth, getStoredAuth, storeAuth, type AuthResponse } from './session'

export { clearStoredAuth, getStoredAuth, storeAuth }
export type { AuthResponse, UserSummary } from './session'

export type MeResponse = {
  id: string
  email: string
}

export type RegisterPayload = {
  fullName: string
  email: string
  password: string
  organizationName: string
  organizationType?: string
}

export type LoginPayload = {
  email: string
  password: string
}

export function register(payload: RegisterPayload) {
  return request<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
    auth: false,
  })
}

export function login(payload: LoginPayload) {
  return request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
    auth: false,
  })
}

export function getMe() {
  return request<MeResponse>('/auth/me')
}

export function logout() {
  const stored = getStoredAuth()
  clearStoredAuth()
  if (!stored?.refreshToken) {
    return Promise.resolve()
  }
  // Best effort — the local session is already cleared either way.
  return request<void>('/auth/logout', {
    method: 'POST',
    body: JSON.stringify({ refreshToken: stored.refreshToken }),
    auth: false,
  }).catch(() => undefined)
}
