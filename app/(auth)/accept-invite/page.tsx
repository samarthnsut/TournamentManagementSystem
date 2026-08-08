'use client'

import { Suspense, useEffect, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useMutation } from '@tanstack/react-query'
import Button from '../../../components/ui/Button'
import Input from '../../../components/ui/Input'
import { ApiError } from '../../../lib/api/client'
import { acceptInvite } from '../../../lib/api/auth'
import { storeAuth } from '../../../lib/api/session'

/** The token arrives in the query string, so this needs Suspense like every other detail page. */
export default function AcceptInvitePage() {
  return (
    <Suspense
      fallback={
        <main className="flex min-h-screen items-center justify-center bg-dark-bg">
          <p className="text-gray-400">Loading…</p>
        </main>
      }
    >
      <AcceptInviteForm />
    </Suspense>
  )
}

/** Turns the API's error codes into something the person holding a dud link can act on. */
function explain(cause: unknown): string {
  if (cause instanceof ApiError) {
    switch (cause.code) {
      case 'INVITE_NOT_FOUND':
        // Accepting clears the token, so a used link lands here too — the message has to cover both.
        return 'This invite link is not valid, or has already been used. If you have set a password, sign in; otherwise ask for a new invite.'
      case 'INVITE_ALREADY_ACCEPTED':
        return 'This invite has already been used. Try signing in instead.'
      case 'INVITE_EXPIRED':
        return 'This invite has expired — they only last seven days. Ask for a new one.'
      default:
        return cause.message
    }
  }
  return cause instanceof Error ? cause.message : 'Something went wrong'
}

function AcceptInviteForm() {
  const router = useRouter()
  const searchParams = useSearchParams()

  const [token, setToken] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [error, setError] = useState('')

  // The emailed link carries it. The field stays visible and editable anyway, so an invite passed
  // on by hand — or one whose email never arrived — can still be pasted in.
  useEffect(() => {
    const fromLink = searchParams.get('token')
    if (fromLink) {
      setToken(fromLink)
    }
  }, [searchParams])

  const accept = useMutation({
    mutationFn: () =>
      acceptInvite({
        inviteToken: token.trim(),
        password,
        displayName: displayName.trim() || undefined,
      }),
    onSuccess: (session) => {
      // The endpoint hands back a real session, so there is no reason to make them sign in again
      // with the password they set two seconds ago.
      storeAuth(session)
      router.push('/dashboard')
    },
    onError: (cause) => setError(explain(cause)),
  })

  const passwordsMatch = password === confirmPassword
  const canSubmit =
    token.trim().length > 0 && password.length >= 8 && passwordsMatch && !accept.isPending

  return (
    <main className="flex min-h-screen items-center justify-center bg-dark-bg px-6 py-12">
      <div className="w-full max-w-md rounded-2xl border border-dark-border bg-dark-surface p-8 shadow-2xl">
        <h1 className="text-center text-2xl font-bold text-white">Accept your invite</h1>
        <p className="mt-2 text-center text-gray-400">
          Choose a password and your account is ready.
        </p>

        {error ? (
          <div
            role="alert"
            className="mt-6 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
          >
            {error}
          </div>
        ) : null}

        <form
          className="mt-6 space-y-4"
          onSubmit={(event) => {
            event.preventDefault()
            setError('')
            accept.mutate()
          }}
        >
          <div>
            <label htmlFor="token" className="mb-2 block text-sm font-medium text-gray-300">
              Invite code
            </label>
            <Input
              id="token"
              value={token}
              onChange={(event) => setToken(event.target.value)}
              placeholder="Paste the code you were sent"
              required
            />
          </div>

          <div>
            <label htmlFor="displayName" className="mb-2 block text-sm font-medium text-gray-300">
              Your name <span className="font-normal text-gray-600">(optional)</span>
            </label>
            <Input
              id="displayName"
              value={displayName}
              onChange={(event) => setDisplayName(event.target.value)}
              placeholder="Leave blank to keep the name you were invited under"
            />
          </div>

          <div>
            <label htmlFor="password" className="mb-2 block text-sm font-medium text-gray-300">
              Choose a password
            </label>
            <Input
              id="password"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
            />
            <p className="mt-1 text-xs text-gray-600">At least 8 characters.</p>
          </div>

          <div>
            <label htmlFor="confirmPassword" className="mb-2 block text-sm font-medium text-gray-300">
              Confirm password
            </label>
            <Input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              value={confirmPassword}
              onChange={(event) => setConfirmPassword(event.target.value)}
              required
            />
            {confirmPassword.length > 0 && !passwordsMatch ? (
              <p className="mt-1 text-xs text-red-300">The two passwords do not match.</p>
            ) : null}
          </div>

          <Button type="submit" className="btn-gradient w-full" disabled={!canSubmit}>
            {accept.isPending ? 'Setting up your account…' : 'Accept invite'}
          </Button>
        </form>

        <p className="mt-6 text-center text-sm text-gray-500">
          Already set up?{' '}
          <Link href="/signin" className="text-accent-cyan transition hover:text-accent-cyan/80">
            Sign in
          </Link>
        </p>
      </div>
    </main>
  )
}
