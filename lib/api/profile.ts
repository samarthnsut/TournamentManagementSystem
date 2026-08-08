import { request } from './client'
import type { RoleAssignment, UserStatus } from './users'

export type Profile = {
  id: string
  email: string
  fullName: string
  phone: string | null
  status: UserStatus
  createdAt: string
  roles: RoleAssignment[]
  permissions: string[]
}

export function getProfile() {
  return request<Profile>('/users/me')
}

export function updateProfile(payload: { fullName: string; phone?: string }) {
  return request<Profile>('/users/me', { method: 'PATCH', body: JSON.stringify(payload) })
}

/**
 * Ends every session on success, including this one — the caller has to sign in again. 204, so
 * there is no body to read.
 */
export function changePassword(payload: { currentPassword: string; newPassword: string }) {
  return request<void>('/users/me/password', { method: 'POST', body: JSON.stringify(payload) })
}
