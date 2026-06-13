'use client'

import { FormEvent, useState } from 'react'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import PasswordInput from '../../../components/auth/PasswordInput'
import Input from '../../../components/ui/Input'
import Button from '../../../components/ui/Button'

const roles = [
  { value: 'athlete', label: 'Athlete' },
  { value: 'organizer', label: 'Tournament organizer' },
  { value: 'federation', label: 'Federation admin' }
]

export default function SignUpPage() {
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [error, setError] = useState('')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const password = String(data.get('password') ?? '')
    const confirm = String(data.get('confirmPassword') ?? '')

    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }

    setError('')
  }

  return (
    <AuthLayout title="Create your account" subtitle="Join Tekspo Infinity to manage sports events">
      <form className="space-y-5" onSubmit={handleSubmit}>
        <div>
          <label htmlFor="name" className="mb-2 block text-sm font-medium text-gray-300">
            Full name
          </label>
          <Input id="name" name="name" placeholder="Alex Morgan" required autoComplete="name" />
        </div>

        <div>
          <label htmlFor="email" className="mb-2 block text-sm font-medium text-gray-300">
            Email address
          </label>
          <Input id="email" type="email" name="email" placeholder="you@organization.com" required autoComplete="email" />
        </div>

        <div>
          <label htmlFor="organization" className="mb-2 block text-sm font-medium text-gray-300">
            Organization
          </label>
          <Input id="organization" name="organization" placeholder="Sports federation or club" required />
        </div>

        <div>
          <label htmlFor="role" className="mb-2 block text-sm font-medium text-gray-300">
            Role
          </label>
          <select
            id="role"
            name="role"
            required
            className="w-full rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-white outline-none transition focus:border-accent-blue focus:ring-2 focus:ring-accent-blue/20"
            defaultValue=""
          >
            <option value="" disabled>
              Select your role
            </option>
            {roles.map((role) => (
              <option key={role.value} value={role.value}>
                {role.label}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="password" className="mb-2 block text-sm font-medium text-gray-300">
            Password
          </label>
          <PasswordInput id="password" name="password" placeholder="At least 8 characters" required minLength={8} autoComplete="new-password" />
        </div>

        <div>
          <label htmlFor="confirmPassword" className="mb-2 block text-sm font-medium text-gray-300">
            Confirm password
          </label>
          <PasswordInput id="confirmPassword" name="confirmPassword" placeholder="Re-enter your password" required minLength={8} autoComplete="new-password" />
        </div>

        <label className="flex cursor-pointer items-start gap-3 text-sm text-gray-300">
          <input
            type="checkbox"
            checked={acceptedTerms}
            onChange={(e) => setAcceptedTerms(e.target.checked)}
            required
            className="mt-0.5 h-4 w-4 shrink-0 rounded border-dark-border bg-dark-surface accent-accent-blue"
          />
          <span>
            I agree to the{' '}
            <AuthLink href="#">Terms of Service</AuthLink> and{' '}
            <AuthLink href="#">Privacy Policy</AuthLink>
          </span>
        </label>

        <Button type="submit" className="btn-gradient w-full" disabled={!acceptedTerms}>
          Create account
        </Button>
        {error ? <p className="text-center text-sm text-accent-pink">{error}</p> : null}
      </form>

      <p className="mt-6 text-center text-sm text-gray-400">
        Already have an account?{' '}
        <AuthLink href="/signin">Sign in</AuthLink>
      </p>
    </AuthLayout>
  )
}
