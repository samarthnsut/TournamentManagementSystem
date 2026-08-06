/**
 * Token storage, kept separate from both `client` and `auth` so the two can depend on it without
 * importing each other.
 */

export type UserSummary = {
  id: string
  displayName: string
  email: string
  status: string
}

export type AuthResponse = {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: UserSummary
}

const AUTH_STORAGE_KEY = 'tms_auth'

export function storeAuth(auth: AuthResponse) {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth))
}

export function getStoredAuth(): AuthResponse | null {
  // Guard for server rendering, where localStorage does not exist.
  if (typeof window === 'undefined') {
    return null
  }

  const raw = localStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthResponse
  } catch {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    return null
  }
}

export function clearStoredAuth() {
  if (typeof window !== 'undefined') {
    localStorage.removeItem(AUTH_STORAGE_KEY)
  }
}
