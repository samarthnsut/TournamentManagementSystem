import { clearStoredAuth, getStoredAuth } from './auth'

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/api/v1'

/** Mirrors the backend's RFC-7807 problem+json body. */
export type ApiErrorBody = {
  code?: string
  detail?: string
  title?: string
  status?: number
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string

  constructor(status: number, code: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

type RequestOptions = RequestInit & { auth?: boolean }

/**
 * Every call goes through here so the bearer token, error shape and expiry handling stay in one
 * place. Pass `auth: false` for the public endpoints, which must work with no token at all.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { auth = true, headers, ...init } = options

  const requestHeaders: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(headers as Record<string, string> | undefined),
  }

  if (auth) {
    const stored = getStoredAuth()
    if (stored?.accessToken) {
      requestHeaders.Authorization = `Bearer ${stored.accessToken}`
    }
  }

  const response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers: requestHeaders })

  if (response.status === 401 && auth) {
    // The token is gone or expired; drop it so the UI stops pretending we are signed in.
    clearStoredAuth()
    throw new ApiError(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null
    throw new ApiError(
      response.status,
      body?.code ?? 'UNKNOWN',
      body?.detail ?? body?.title ?? `Request failed with status ${response.status}`,
    )
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
