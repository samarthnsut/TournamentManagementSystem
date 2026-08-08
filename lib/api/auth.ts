import { request } from './client'
import { clearStoredAuth, getStoredAuth, storeAuth, type AuthResponse } from './session'

export { clearStoredAuth, getStoredAuth, storeAuth }
export type { AuthResponse, UserSummary } from './session'

export type MeResponse = {
  id: string
  email: string
  /** Permission codes the caller holds anywhere; the API still enforces scope on every call. */
  permissions: string[]
  roles: string[]
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

export type AcceptInvitePayload = {
  inviteToken: string
  password: string
  displayName?: string
}

/**
 * Redeems an invite and returns a full session — the invitee is signed in on the spot rather than
 * being bounced to a login form to type the password they just chose.
 *
 * 404 INVITE_NOT_FOUND, 409 INVITE_ALREADY_ACCEPTED, 409 INVITE_EXPIRED.
 */
export function acceptInvite(payload: AcceptInvitePayload) {
  return request<AuthResponse>('/auth/invite-accept', {
    method: 'POST',
    body: JSON.stringify(payload),
    auth: false,
  })
}

/**
 * Always resolves for a well-formed address, whether or not it has an account — the API answers
 * identically either way so that this cannot be used to discover who is registered. Do not add a
 * "no such user" branch here; there is nothing to branch on.
 */
export function forgotPassword(email: string) {
  return request<void>('/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify({ email }),
    auth: false,
  })
}

/** 404 RESET_TOKEN_NOT_FOUND, 409 RESET_TOKEN_EXPIRED. Ends every session on success. */
export function resetPassword(payload: { token: string; newPassword: string }) {
  return request<void>('/auth/reset-password', {
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
