'use client'

import { FormEvent, useState } from 'react'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import Input from '../../../components/ui/Input'
import Button from '../../../components/ui/Button'

export default function ForgotPasswordPage() {
  const [submitted, setSubmitted] = useState(false)
  const [email, setEmail] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    setEmail(String(data.get('email') ?? ''))
    setSubmitted(true)
  }

  if (submitted) {
    return (
      <AuthLayout title="Check your email" subtitle="We sent password reset instructions">
        <div className="rounded-xl border border-accent-blue/30 bg-accent-blue/10 p-5 text-center">
          <p className="text-sm text-gray-300">
            If an account exists for <span className="font-medium text-white">{email}</span>, you will
            receive a reset link shortly.
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

        <Button type="submit" className="btn-gradient w-full">
          Send reset link
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-400">
        Remember your password?{' '}
        <AuthLink href="/signin">Sign in</AuthLink>
      </p>
    </AuthLayout>
  )
}
