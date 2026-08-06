'use client'

import { useCallback, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { getMe, logout as logoutRequest } from './api/auth'
import { AUTH_CHANGED_EVENT, getStoredAuth, type UserSummary } from './api/session'

type AuthState = {
  user: UserSummary | null
  isAuthenticated: boolean
  /**
   * Whether the caller holds a permission. Used only to avoid offering actions that would fail —
   * the API remains the authority, and scope is still checked there on every call.
   */
  can: (permission: string) => boolean
  /** True until permissions have loaded, so the UI can avoid flashing controls on and off. */
  isLoadingPermissions: boolean
  /**
   * True until the first client-side read completes. The app is statically exported, so the markup
   * is built with no session at all — rendering signed-in UI before this flips would produce a
   * hydration mismatch and a visible flash of the wrong nav.
   */
  isLoading: boolean
  signOut: () => Promise<void>
}

export function useAuth(): AuthState {
  const router = useRouter()
  const [user, setUser] = useState<UserSummary | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [permissions, setPermissions] = useState<string[] | null>(null)

  useEffect(() => {
    const sync = () => {
      const next = getStoredAuth()?.user ?? null
      setUser(next)
      if (!next) {
        setPermissions(null)
        return
      }
      // Permissions live on the server; asking once per session keeps the UI honest.
      //
      // If they cannot be determined — an older backend, a failed call — fall open rather than
      // shut. Hiding every control makes the app look broken, while showing one the caller cannot
      // use costs them a clear error from the API, which enforces this regardless.
      getMe()
        .then((me) => setPermissions(me.permissions ?? null))
        .catch(() => setPermissions(null))
    }

    sync()
    setIsLoading(false)

    // Same tab (our own sign-in/out) and other tabs respectively.
    window.addEventListener(AUTH_CHANGED_EVENT, sync)
    window.addEventListener('storage', sync)
    return () => {
      window.removeEventListener(AUTH_CHANGED_EVENT, sync)
      window.removeEventListener('storage', sync)
    }
  }, [])

  const signOut = useCallback(async () => {
    await logoutRequest()
    router.push('/')
  }, [router])

  const can = useCallback(
    (permission: string) => permissions === null || permissions.includes(permission),
    [permissions],
  )

  return {
    user,
    isAuthenticated: user !== null,
    isLoading,
    isLoadingPermissions: false,
    can,
    signOut,
  }
}
