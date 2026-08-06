'use client'

import Link from 'next/link'
import Button from './ui/Button'
import { useAuth } from '../lib/useAuth'

/** The closing pitch on the home page. Signed-in users get somewhere to go instead of a sales pitch. */
export default function CallToAction() {
  const { isAuthenticated, isLoading, can } = useAuth()

  return (
    <section className="mx-auto max-w-7xl px-6 py-20 sm:py-28">
      <div className="relative overflow-hidden rounded-3xl bg-gradient-brand p-[1px]">
        <div className="relative rounded-[23px] bg-dark-bg px-8 py-12 text-center sm:px-12 sm:py-16">
          <div className="pointer-events-none absolute inset-0 opacity-40">
            <div className="absolute -left-20 top-0 h-48 w-48 rounded-full bg-accent-orange/40 blur-3xl" />
            <div className="absolute -right-20 bottom-0 h-48 w-48 rounded-full bg-accent-blue/40 blur-3xl" />
          </div>
          <div className="relative">
            <h2 className="text-3xl font-bold sm:text-4xl">
              {isAuthenticated ? 'Ready for your next event?' : 'Ready to transform your next event?'}
            </h2>
            <p className="mx-auto mt-4 max-w-xl text-gray-300">
              {isAuthenticated
                ? 'Set up a new tournament, add its competitions, and publish when everything is in place.'
                : 'Launch your first tournament portal in minutes and scale across federations with confidence.'}
            </p>
            <div className="mt-8 flex min-h-[3.25rem] flex-col justify-center gap-4 sm:flex-row">
              {isLoading ? null : isAuthenticated ? (
                <>
                  {can('tournament:create') ? (
                    <Link href="/dashboard/create">
                      <Button className="btn-gradient w-full sm:w-auto">Create a tournament</Button>
                    </Link>
                  ) : null}
                  <Link href="/dashboard">
                    <Button variant="secondary" className="w-full sm:w-auto">
                      View my tournaments
                    </Button>
                  </Link>
                </>
              ) : (
                <>
                  <Link href="/signup">
                    <Button className="btn-gradient w-full sm:w-auto">Start free trial</Button>
                  </Link>
                  <Link href="/signin">
                    <Button variant="secondary" className="w-full sm:w-auto">
                      Talk to sales
                    </Button>
                  </Link>
                </>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  )
}
