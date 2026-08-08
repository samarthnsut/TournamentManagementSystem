import { clearStoredAuth, getStoredAuth } from './session'

// `||`, not `??`: an undefined GitHub Actions variable expands to an empty string, and `??` would
// keep it. An empty base turns every call into a same-origin relative path, so the static host
// answers the login POST with 405 and the backend is never reached.
export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/api/v1'

/** One field-level complaint from bean validation, as GlobalExceptionHandler emits them. */
export type FieldError = { field: string; message: string }

/** Mirrors the backend's RFC-7807 problem+json body. */
export type ApiErrorBody = {
  code?: string
  detail?: string
  title?: string
  status?: number
  errors?: FieldError[]
}

export class ApiError extends Error {
  readonly status: number
  readonly code: string
  /** Empty unless the server rejected specific fields; keyed lookups use {@link fieldMessages}. */
  readonly fieldErrors: FieldError[]

  constructor(status: number, code: string, message: string, fieldErrors: FieldError[] = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.fieldErrors = fieldErrors
  }

  /** Field name → message, for wiring straight into form state. */
  fieldMessages(): Record<string, string> {
    const messages: Record<string, string> = {}
    this.fieldErrors.forEach((error) => {
      messages[error.field] = error.message
    })
    return messages
  }
}

/** Turns a field name like `organizationUnitId` into something a person would say. */
function humanizeField(field: string) {
  const spaced = field
    .replace(/\.?([A-Z])/g, (_, letter: string) => ` ${letter.toLowerCase()}`)
    .replace(/^\./, '')
    .replace(/\bid\b/i, '')
    .trim()
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
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

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...init, headers: requestHeaders })
  } catch (cause) {
    // fetch rejects only on a transport failure — the API being down is by far the likeliest, and
    // "Failed to fetch" in a red box tells an organizer nothing they can act on.
    throw new ApiError(
      0,
      'NETWORK_UNREACHABLE',
      `Could not reach the server at ${API_BASE_URL}. Check that the backend is running.`,
    )
  }

  if (response.status === 401 && auth) {
    // The token is gone or expired; drop it so the UI stops pretending we are signed in.
    clearStoredAuth()
    throw new ApiError(401, 'UNAUTHENTICATED', 'Your session has expired. Please sign in again.')
  }

  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null
    const fieldErrors = body?.errors ?? []

    // "Request body failed validation." tells nobody which field is wrong, and the server already
    // said. Fold the per-field complaints into the message so even a caller that never inspects
    // fieldErrors shows something actionable.
    const detail =
      fieldErrors.length > 0
        ? fieldErrors.map((error) => `${humanizeField(error.field)} ${error.message}`).join('; ')
        : body?.detail ?? body?.title ?? `Request failed with status ${response.status}`

    throw new ApiError(response.status, body?.code ?? 'UNKNOWN', detail, fieldErrors)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}
