'use client'

import { FormEvent, useState } from 'react'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import PasswordInput from '../../../components/auth/PasswordInput'
import Input from '../../../components/ui/Input'
import Button from '../../../components/ui/Button'

export default function SignInPage() {
  const [remember, setRemember] = useState(false)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to manage tournaments and events">
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div>
          <label htmlFor="email" className="mb-2 block text-sm font-medium text-gray-300">
            Email address
          </label>
          <Input id="email" type="email" name="email" placeholder="you@organization.com" required autoComplete="email" />
        </div>

        <div>
          <div className="mb-2 flex items-center justify-between">
            <label htmlFor="password" className="text-sm font-medium text-gray-300">
              Password
            </label>
            <AuthLink href="/forgot-password">Forgot password?</AuthLink>
          </div>
          <PasswordInput id="password" name="password" placeholder="Enter your password" required autoComplete="current-password" />
        </div>

        <label className="flex cursor-pointer items-center gap-3 text-sm text-gray-300">
          <input
            type="checkbox"
            checked={remember}
            onChange={(e) => setRemember(e.target.checked)}
            className="h-4 w-4 rounded border-dark-border bg-dark-surface accent-accent-blue"
          />
          Remember me
        </label>

        <Button type="submit" className="btn-gradient w-full">
          Sign in
        </Button>
      </form>

      <p className="mt-6 text-center text-sm text-gray-400">
        Don&apos;t have an account?{' '}
        <AuthLink href="/signup">Sign up</AuthLink>
      </p>
    </AuthLayout>
  )
}
