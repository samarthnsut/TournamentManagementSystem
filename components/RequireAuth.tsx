'use client'

import { useEffect, type ReactNode } from 'react'
import { useRouter } from 'next/navigation'
import { useAuth } from '../lib/useAuth'

/**
 * Gates a page behind a session.
 *
 * This is a convenience, not a security boundary — the data itself is protected by the API, which
 * rejects an unauthenticated call regardless of what the browser renders. Its job is to send a
 * signed-out visitor somewhere useful instead of showing them a page full of 401 errors.
 */
export default function RequireAuth({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()
  const router = useRouter()

  useEffect(() => {
    if (!isLoading && !isAuthenticated) {
      router.replace('/signin')
    }
  }, [isAuthenticated, isLoading, router])

  if (isLoading) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-dark-bg">
        <p className="text-gray-400">Loading…</p>
      </main>
    )
  }

  if (!isAuthenticated) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-dark-bg">
        <p className="text-gray-400">Redirecting to sign in…</p>
      </main>
    )
  }

  return <>{children}</>
}
