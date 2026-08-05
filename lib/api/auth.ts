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

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1'
const AUTH_STORAGE_KEY = 'tms_auth'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  })

  if (!response.ok) {
    const fallbackMessage = `Request failed with status ${response.status}`
    const errorBody = await response.json().catch(() => null) as { message?: string } | null
    throw new Error(errorBody?.message ?? fallbackMessage)
  }

  return response.json() as Promise<T>
}

export function register(payload: RegisterPayload) {
  return request<AuthResponse>('/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function login(payload: LoginPayload) {
  return request<AuthResponse>('/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getMe() {
  const auth = getStoredAuth()
  return request<MeResponse>('/auth/me', {
    headers: auth ? { Authorization: `Bearer ${auth.accessToken}` } : undefined,
  })
}

export function storeAuth(auth: AuthResponse) {
  localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(auth))
}

export function getStoredAuth(): AuthResponse | null {
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
  localStorage.removeItem(AUTH_STORAGE_KEY)
}
