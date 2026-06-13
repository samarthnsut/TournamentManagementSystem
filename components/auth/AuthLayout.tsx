import Link from 'next/link'
import { ReactNode } from 'react'
import Logo from '../Logo'

type AuthLayoutProps = {
  children: ReactNode
  title: string
  subtitle?: string
}

export default function AuthLayout({ children, title, subtitle }: AuthLayoutProps) {
  return (
    <div className="relative flex min-h-screen flex-col bg-dark-bg">
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -right-24 top-20 h-72 w-72 animate-float rounded-full bg-accent-orange/25 blur-3xl" />
        <div className="absolute left-0 top-1/3 h-64 w-64 animate-float-delayed rounded-full bg-accent-pink/20 blur-3xl" />
        <div className="absolute bottom-0 right-1/4 h-80 w-80 animate-pulse-glow rounded-full bg-accent-blue/20 blur-3xl" />
      </div>

      <header className="relative z-10 px-6 py-6">
        <Link href="/">
          <Logo />
        </Link>
      </header>

      <main className="relative z-10 flex flex-1 items-center justify-center px-6 pb-12">
        <div className="w-full max-w-md">
          <div className="glass-panel border-white/10 p-8 shadow-2xl shadow-black/20 sm:p-10">
            <div className="mb-8 text-center">
              <h1 className="text-2xl font-bold text-white sm:text-3xl">{title}</h1>
              {subtitle ? <p className="mt-2 text-sm text-gray-400 sm:text-base">{subtitle}</p> : null}
            </div>
            {children}
          </div>
        </div>
      </main>
    </div>
  )
}
