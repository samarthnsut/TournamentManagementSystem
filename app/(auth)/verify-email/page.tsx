'use client'

import { useSearchParams } from 'next/navigation'
import { Suspense, useEffect, useState } from 'react'
import Link from 'next/link'
import AuthLayout from '../../../components/auth/AuthLayout'
import AuthLink from '../../../components/auth/AuthLink'
import Button from '../../../components/ui/Button'

type VerifyState = 'loading' | 'success' | 'error'

function VerifyEmailContent() {
  const searchParams = useSearchParams()
  const statusParam = searchParams.get('status')
  const [state, setState] = useState<VerifyState>(() => {
    if (statusParam === 'success') return 'success'
    if (statusParam === 'error') return 'error'
    return 'loading'
  })
  const [resent, setResent] = useState(false)

  useEffect(() => {
    if (statusParam === 'success' || statusParam === 'error') return
    const timer = setTimeout(() => setState('success'), 2000)
    return () => clearTimeout(timer)
  }, [statusParam])

  if (state === 'loading') {
    return (
      <AuthLayout title="Verifying email" subtitle="Please wait while we confirm your address">
        <div className="flex flex-col items-center gap-4 py-4">
          <div className="h-10 w-10 animate-spin rounded-full border-2 border-white/20 border-t-accent-blue" />
          <p className="text-sm text-gray-400">This usually takes a few seconds…</p>
        </div>
      </AuthLayout>
    )
  }

  if (state === 'success') {
    return (
      <AuthLayout title="Email verified" subtitle="Welcome to Tekspo Infinity">
        <div className="rounded-xl border border-accent-blue/30 bg-accent-blue/10 p-5 text-center">
          <p className="text-sm text-gray-300">
            Your email has been verified. You&apos;re all set to manage tournaments and events.
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
    <AuthLayout title="Verification failed" subtitle="We couldn&apos;t verify your email address">
      <div className="rounded-xl border border-accent-pink/30 bg-accent-pink/10 p-5 text-center">
        <p className="text-sm text-gray-300">
          The link may have expired or already been used. Request a new verification email below.
        </p>
      </div>

      <div className="mt-6 space-y-4">
        <Button
          className="btn-gradient w-full"
          onClick={() => {
            setResent(true)
            setState('loading')
            setTimeout(() => setState('success'), 1500)
          }}
        >
          {resent ? 'Email sent — verifying…' : 'Resend verification email'}
        </Button>
        <p className="text-center text-sm text-gray-400">
          <AuthLink href="/signin">Back to sign in</AuthLink>
        </p>
      </div>
    </AuthLayout>
  )
}

export default function VerifyEmailPage() {
  return (
    <Suspense
      fallback={
        <AuthLayout title="Verifying email" subtitle="Please wait while we confirm your address">
          <div className="flex justify-center py-8">
            <div className="h-10 w-10 animate-spin rounded-full border-2 border-white/20 border-t-accent-blue" />
          </div>
        </AuthLayout>
      }
    >
      <VerifyEmailContent />
    </Suspense>
  )
}
