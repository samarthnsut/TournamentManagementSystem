'use client'

import { useCallback, useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { logout as logoutRequest } from './api/auth'
import { AUTH_CHANGED_EVENT, getStoredAuth, type UserSummary } from './api/session'

type AuthState = {
  user: UserSummary | null
  isAuthenticated: boolean
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

  useEffect(() => {
    const sync = () => setUser(getStoredAuth()?.user ?? null)

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

  return { user, isAuthenticated: user !== null, isLoading, signOut }
}
