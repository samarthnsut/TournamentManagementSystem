'use client'

import { FormEvent, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import Input from '../../../components/ui/Input'
import Button from '../../../components/ui/Button'
import { forgotPassword } from '../../../lib/api/auth'

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false)
  const [email, setEmail] = useState('')
  const [error, setError] = useState('')

  const requestReset = useMutation({
    mutationFn: (address: string) => forgotPassword(address),
    // Success and "no such account" are the same response by design, so the confirmation below is
    // shown either way — the UI must not become the oracle the API refuses to be.
    onSuccess: () => setSubmitted(true),
    // Only a transport or server failure reaches here; anything else already resolved.
    onError: (cause) =>
      setError(cause instanceof Error ? cause.message : 'Could not send the reset link'),
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    const data = new FormData(event.currentTarget)
    const address = String(data.get('email') ?? '')
    setEmail(address)
    requestReset.mutate(address)
  }

  if (submitted) {
    return (
      <AuthLayout title="Check your email" subtitle="We sent password reset instructions">
        <div className="rounded-xl border border-accent-blue/30 bg-accent-blue/10 p-5 text-center">
          <p className="text-sm text-gray-300">
            If an account exists for <span className="font-medium text-white">{email}</span>, you will
            receive a reset link shortly. It is valid for one hour.
          </p>
        </div>
        <div className="mt-6 text-center">
          <AuthLink href="/signin">Back to sign in</AuthLink>
        </div>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout title="Forgot password?" subtitle="Enter your email and we&apos;ll send reset instructions">
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email" className="mb-2 block text-sm font-medium text-gray-300">
            Email address
          </label>
          <Input id="email" type="email" name="email" placeholder="you@organization.com" required autoComplete="email" />
        </div>

        {error ? (
          <div
            role="alert"
            className="rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
          >
            {error}
          </div>
        ) : null}

        <Button type="submit" className="btn-gradient w-full" disabled={requestReset.isPending}>
          {requestReset.isPending ? 'Sending…' : 'Send reset link'}
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-400">
        Remember your password?{' '}
        <AuthLink href="/signin">Sign in</AuthLink>
      </p>
    </AuthLayout>
  )
}
