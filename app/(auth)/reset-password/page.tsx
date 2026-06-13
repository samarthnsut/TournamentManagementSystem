'use client'

import { FormEvent, useState } from 'react'
import Link from 'next/link'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import PasswordInput from '../../../components/auth/PasswordInput'
import Button from '../../../components/ui/Button'

export default function ResetPasswordPage() {
  const [submitted, setSubmitted] = useState(false)
  const [error, setError] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const password = String(data.get('password') ?? '')
    const confirm = String(data.get('confirmPassword') ?? '')

    if (password.length < 8) {
      setError('Password must be at least 8 characters.')
      return
    }

    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }

    setError('')
    setSubmitted(true)
  }

  if (submitted) {
    return (
      <AuthLayout title="Password updated" subtitle="Your password has been reset successfully">
        <div className="rounded-xl border border-accent-orange/30 bg-accent-orange/10 p-5 text-center">
          <p className="text-sm text-gray-300">
            You can now sign in with your new password.
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

        <Button type="submit" className="btn-gradient w-full">
          Update password
        </Button>
        {error ? <p className="text-center text-sm text-accent-pink">{error}</p> : null}
      </form>

      <p className="mt-6 text-center text-sm text-gray-400">
        <AuthLink href="/signin">Back to sign in</AuthLink>
      </p>
    </AuthLayout>
  )
}
