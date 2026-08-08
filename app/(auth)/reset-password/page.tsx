'use client'

import { FormEvent, Suspense, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { useMutation } from '@tanstack/react-query'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import PasswordInput from '../../../components/auth/PasswordInput'
import Button from '../../../components/ui/Button'
import { ApiError } from '../../../lib/api/client'
import { resetPassword } from '../../../lib/api/auth'

/** The token rides in the query string, so this needs Suspense like every other such page. */
export default function ResetPasswordPage() {
  return (
    <Suspense
      fallback={
        <AuthLayout title="Reset password" subtitle="Loading…">
          <p className="text-center text-sm text-gray-400">One moment…</p>
        </AuthLayout>
      }
    >
      <ResetPasswordForm />
    </Suspense>
  )
}

/** Turns the API's codes into something the holder of a stale link can act on. */
function explain(cause: unknown): string {
  if (cause instanceof ApiError) {
    switch (cause.code) {
      case 'RESET_TOKEN_NOT_FOUND':
        return 'This reset link is not valid, or has already been used. Request a new one.'
      case 'RESET_TOKEN_EXPIRED':
        return 'This reset link has expired — they last one hour. Request a new one.'
      default:
        return cause.message
    }
  }
  return cause instanceof Error ? cause.message : 'Could not reset your password'
}

function ResetPasswordForm() {
  const searchParams = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')

  const submit = useMutation({
    mutationFn: (newPassword: string) => resetPassword({ token, newPassword }),
    onSuccess: () => {
      setError('')
      setSubmitted(true)
    },
    onError: (cause) => setError(explain(cause)),
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const password = String(data.get('password') ?? '')
    const confirm = String(data.get('confirmPassword') ?? '')

    if (!token) {
      setError('This link is missing its reset code. Use the link from your email.')
      return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }
    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }

    setError('')
    submit.mutate(password)
  }

  if (submitted) {
    return (
      <AuthLayout title="Password updated" subtitle="Your password has been reset successfully">
        <div className="rounded-xl border border-accent-orange/30 bg-accent-orange/10 p-5 text-center">
          <p className="text-sm text-gray-300">
            You can now sign in with your new password. Any other devices you were signed in on have
            been signed out.
          </p>
        </div>
        <div className="mt-6">
          <Link href="/signin">
            <Button className="btn-gradient w-full">Continue to sign in</Button>
          </Link>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Reset password" subtitle="Choose a new password for your account">
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div>
          <label htmlFor="password" className="mb-2 block text-sm font-medium text-gray-300">
            New password
          </label>
          <PasswordInput id="password" name="password" placeholder="At least 8 characters" required minLength={8} autoComplete="new-password" />
        </div>

        <div>
          <label htmlFor="confirmPassword" className="mb-2 block text-sm font-medium text-gray-300">
            Confirm new password
          </label>
          <PasswordInput id="confirmPassword" name="confirmPassword" placeholder="Re-enter your password" required minLength={8} autoComplete="new-password" />
        </div>

        <p className="text-xs text-gray-500">Password must be at least 8 characters long.</p>

        <Button type="submit" className="btn-gradient w-full" disabled={submit.isPending}>
          {submit.isPending ? 'Updating…' : 'Update password'}
        </Button>
        {error ? <p className="text-center text-sm text-accent-pink">{error}</p> : null}
      </form>

      <p className="mt-6 text-center text-sm text-gray-400">
        <AuthLink href="/signin">Back to sign in</AuthLink>
      </p>
    </AuthLayout>
  )
}
