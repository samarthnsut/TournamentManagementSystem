'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import Button from './ui/Button'
import Logo from './Logo'
import { useAuth } from '../lib/useAuth'

export default function Header() {
  const { user, isAuthenticated, isLoading, signOut } = useAuth()
  const pathname = usePathname()

  // No point offering a link to the page you are already on.
  const isOnCreatePage = pathname === '/dashboard/create'

  return (
    <header className="sticky top-0 z-50 border-b border-white/10 bg-dark-bg/80 backdrop-blur-lg">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <Link href="/">
          <Logo />
        </Link>

        <nav className="flex flex-wrap items-center gap-5 text-sm sm:gap-6">
          <Link href="/" className="text-gray-300 transition hover:text-white">
            Home
          </Link>
          <Link href="/#features" className="text-gray-300 transition hover:text-white">
            Features
          </Link>

          {isAuthenticated ? (
            <>
              <Link href="/dashboard" className="text-gray-300 transition hover:text-white">
                Dashboard
              </Link>
              <Link href="/dashboard/approvals" className="text-gray-300 transition hover:text-white">
                Approvals
              </Link>
              {isOnCreatePage ? null : (
                <Link href="/dashboard/create">
                  <Button className="btn-gradient whitespace-nowrap px-4 py-2 text-sm">
                    Create Tournament
                  </Button>
                </Link>
              )}
              <span className="hidden text-gray-400 sm:inline" title={user?.email}>
                {user?.displayName}
              </span>
              <Button variant="ghost" className="px-3 py-2 text-sm" onClick={() => void signOut()}>
                Sign out
              </Button>
            </>
          ) : isLoading ? (
            // Reserve the space so the nav does not jump once the session is known.
            <span className="h-9 w-40" aria-hidden="true" />
          ) : (
            <>
              <Link href="/signin">
                <Button variant="ghost" className="px-3 py-2 text-sm">
                  Login
                </Button>
              </Link>
              <Link href="/signup">
                <Button className="btn-gradient px-5 py-2 text-sm">Get started</Button>
              </Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}
