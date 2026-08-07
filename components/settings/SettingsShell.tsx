'use client'

import { ReactNode } from 'react'
import Header from '../Header'
import RequireAuth from '../RequireAuth'
import SettingsNav from './SettingsNav'

export default function SettingsShell({
  title,
  description,
  actions,
  error,
  children,
}: {
  title: string
  description?: string
  actions?: ReactNode
  error?: string
  children: ReactNode
}) {
  return (
    <RequireAuth>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="mx-auto max-w-6xl px-6 py-8 sm:px-8 sm:py-12">
          <SettingsNav />

          <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
            <div>
              <h1 className="text-2xl font-bold text-white sm:text-3xl">{title}</h1>
              {description ? <p className="mt-2 text-gray-400">{description}</p> : null}
            </div>
            {actions}
          </div>

          {error ? (
            <div className="mb-6 rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-3 text-sm text-red-300">
              {error}
            </div>
          ) : null}

          {children}
        </div>
      </main>
    </RequireAuth>
  )
}
