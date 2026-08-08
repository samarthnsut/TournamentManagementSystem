'use client'

import { FormEvent, useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation } from '@tanstack/react-query'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import PasswordInput from '../../../components/auth/PasswordInput'
import Input from '../../../components/ui/Input'
import Button from '../../../components/ui/Button'
import Select from '../../../components/ui/Select'
import { register, storeAuth } from '../../../lib/api/auth'

const roles = [
  { value: 'athlete', label: 'Athlete' },
  { value: 'organizer', label: 'Tournament organizer' },
  { value: 'federation', label: 'Federation admin' }
]

export default function SignUpPage() {
  const router = useRouter()
  const [acceptedTerms, setAcceptedTerms] = useState(false)
  const [error, setError] = useState('')
  const registerMutation = useMutation({
    mutationFn: register,
    onSuccess: (auth) => {
      storeAuth(auth)
      router.push('/dashboard')
    },
    onError: (mutationError) => {
      setError(mutationError instanceof Error ? mutationError.message : 'Unable to create account.')
    },
  })

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const password = String(data.get('password') ?? '')
    const confirm = String(data.get('confirmPassword') ?? '')
    const role = String(data.get('role') ?? '')

    // The role lives in a hidden input, and browsers exempt those from constraint validation, so
    // `required` on it does nothing — check it here instead.
    if (!role) {
      setError('Please select your role.')
      return
    }

    if (password !== confirm) {
      setError('Passwords do not match.')
      return
    }

    // Kept as an explicit message rather than a disabled button: a button that does nothing when
    // clicked reads as broken, with no hint about what is missing.
    if (!acceptedTerms) {
      setError('Please accept the Terms of Service and Privacy Policy to continue.')
      return
    }

    setError('')
    registerMutation.mutate({
      fullName: String(data.get('name') ?? ''),
      email: String(data.get('email') ?? ''),
      password,
      organizationName: String(data.get('organization') ?? ''),
      organizationType: String(data.get('role') ?? '') === 'federation' ? 'FEDERATION' : 'PRIVATE_ORGANIZER',
    })
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
          <Select id="role" name="role" required options={roles} placeholder="Select your role" />
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
            {/* One link, because there is one document. Naming a separate privacy policy that does
                not exist would be worse than not offering the link — how data is handled is
                sections 3 and 4 of the terms. */}
            I agree to the <AuthLink href="/terms">Terms of use</AuthLink>
          </span>
        </label>

        <Button type="submit" className="btn-gradient w-full" disabled={registerMutation.isPending}>
          {registerMutation.isPending ? 'Creating account...' : 'Create account'}
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
