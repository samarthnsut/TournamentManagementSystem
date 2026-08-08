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

/**
 * The browser only fires `storage` in *other* tabs, so components in this one would not notice a
 * sign-in until a reload. This event closes that gap.
 */
export const AUTH_CHANGED_EVENT = 'tms:auth-changed'

function announceChange() {
  if (typeof window !== 'undefined') {
    window.dispatchEvent(new Event(AUTH_CHANGED_EVENT))
  }
}

/**
 * "Remember me" decides *where* the session is kept, not how long the server honours it.
 *
 * - remembered → `localStorage`, survives closing the browser
 * - not remembered → `sessionStorage`, gone when the tab closes
 *
 * That distinction is the one people actually mean on a shared or public machine. The refresh
 * token's own 30-day lifetime is unchanged either way — this is about what the browser retains,
 * and the server remains the authority on what is still valid.
 */
export function storeAuth(auth: AuthResponse, remember = true) {
  if (typeof window === 'undefined') {
    return
  }

  const serialized = JSON.stringify(auth)
  if (remember) {
    localStorage.setItem(AUTH_STORAGE_KEY, serialized)
    // Never leave a copy in the other store, or signing out of one would look like it failed.
    sessionStorage.removeItem(AUTH_STORAGE_KEY)
  } else {
    sessionStorage.setItem(AUTH_STORAGE_KEY, serialized)
    localStorage.removeItem(AUTH_STORAGE_KEY)
  }
  announceChange()
}

export function getStoredAuth(): AuthResponse | null {
  // Guard for server rendering, where neither store exists.
  if (typeof window === 'undefined') {
    return null
  }

  // sessionStorage first: a "just this once" sign-in in this tab should win over anything an
  // earlier remembered session left behind.
  const raw = sessionStorage.getItem(AUTH_STORAGE_KEY) ?? localStorage.getItem(AUTH_STORAGE_KEY)
  if (!raw) {
    return null
  }

  try {
    return JSON.parse(raw) as AuthResponse
  } catch {
    clearStoredAuth()
    return null
  }
}

/** Clears both stores; which one held the session is not the caller's concern. */
export function clearStoredAuth() {
  if (typeof window !== 'undefined') {
    localStorage.removeItem(AUTH_STORAGE_KEY)
    sessionStorage.removeItem(AUTH_STORAGE_KEY)
    announceChange()
  }
}
