'use client'

import { useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { usePathname } from 'next/navigation'
import Button from './ui/Button'
import Logo from './Logo'
import { useAuth } from '../lib/useAuth'

/**
 * Signed out, this is a marketing header. Signed in, it is an application header, and the two want
 * different things: a visitor needs Home and Features, an organizer needs their work.
 *
 * Everything administrative moved into a single account menu. Seven flat links meant the important
 * one — Create tournament — competed with Sign out for attention.
 */
export default function Header() {
  const { user, isAuthenticated, isLoading, can, signOut } = useAuth()
  const pathname = usePathname()
  const [isMenuOpen, setIsMenuOpen] = useState(false)
  const menuRef = useRef<HTMLDivElement>(null)

  const isOnCreatePage = pathname === '/dashboard/create'

  useEffect(() => {
    if (!isMenuOpen) {
      return
    }
    const onPointerDown = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setIsMenuOpen(false)
      }
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsMenuOpen(false)
      }
    }
    document.addEventListener('mousedown', onPointerDown)
    window.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointerDown)
      window.removeEventListener('keydown', onKey)
    }
  }, [isMenuOpen])

  // A navigation should close the menu it was launched from.
  useEffect(() => setIsMenuOpen(false), [pathname])

  const menuItems = [
    { href: '/dashboard/settings/users', label: 'People & roles', permission: 'user:read' },
    { href: '/dashboard/settings/venues', label: 'Venues', permission: 'venue:read' },
    { href: '/dashboard/settings/sports', label: 'Sport configurations', permission: 'sport-config:read' },
    { href: '/dashboard/settings/workflows', label: 'Approval workflows', permission: 'approval:read' },
    { href: '/dashboard/audit', label: 'Audit trail', permission: 'audit:read' },
  ].filter((item) => can(item.permission))

  const isActive = (href: string) => pathname === href || pathname.startsWith(`${href}/`)

  return (
    <header className="sticky top-0 z-50 border-b border-white/10 bg-dark-bg/80 backdrop-blur-lg">
      <div className="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-4 sm:flex-row sm:items-center sm:justify-between">
        <Link href={isAuthenticated ? '/dashboard' : '/'}>
          <Logo />
        </Link>

        <nav className="flex flex-wrap items-center gap-2 text-sm sm:gap-3">
          {isAuthenticated ? (
            <>
              <Link
                href="/dashboard"
                className={`rounded-lg px-3 py-2 transition ${
                  isActive('/dashboard') && pathname === '/dashboard'
                    ? 'bg-white/10 text-white'
                    : 'text-gray-300 hover:bg-white/5 hover:text-white'
                }`}
              >
                Tournaments
              </Link>

              {can('registration:approve') ? (
                <Link
                  href="/dashboard/approvals"
                  className={`rounded-lg px-3 py-2 transition ${
                    isActive('/dashboard/approvals')
                      ? 'bg-white/10 text-white'
                      : 'text-gray-300 hover:bg-white/5 hover:text-white'
                  }`}
                >
                  Approvals
                </Link>
              ) : null}

              {isOnCreatePage || !can('tournament:create') ? null : (
                <Link href="/dashboard/create">
                  <Button className="btn-gradient whitespace-nowrap px-4 py-2 text-sm">
                    New tournament
                  </Button>
                </Link>
              )}

              <div className="relative" ref={menuRef}>
                <button
                  type="button"
                  aria-haspopup="menu"
                  aria-expanded={isMenuOpen}
                  onClick={() => setIsMenuOpen(!isMenuOpen)}
                  className="flex items-center gap-2 rounded-full border border-white/15 py-1.5 pl-1.5 pr-3 text-gray-200 transition hover:border-accent-purple/50 hover:text-white"
                >
                  <span
                    aria-hidden="true"
                    className="flex h-7 w-7 items-center justify-center rounded-full bg-gradient-brand text-xs font-semibold text-white"
                  >
                    {(user?.displayName ?? '?').charAt(0).toUpperCase()}
                  </span>
                  <span className="hidden max-w-[10rem] truncate sm:inline">{user?.displayName}</span>
                  <svg className="h-4 w-4 text-gray-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path
                      fillRule="evenodd"
                      d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.17l3.71-3.94a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z"
                      clipRule="evenodd"
                    />
                  </svg>
                </button>

                {isMenuOpen ? (
                  <div
                    role="menu"
                    className="absolute right-0 z-50 mt-2 w-60 overflow-hidden rounded-xl border border-dark-border bg-dark-surface shadow-2xl"
                  >
                    <div className="border-b border-dark-border px-4 py-3">
                      <p className="truncate text-sm font-medium text-white">{user?.displayName}</p>
                      <p className="truncate text-xs text-gray-500">{user?.email}</p>
                    </div>

                    <div className="border-b border-dark-border py-1">
                      <Link
                        href="/dashboard/profile"
                        role="menuitem"
                        className="block px-4 py-2 text-sm text-gray-300 transition hover:bg-white/5 hover:text-white"
                      >
                        Your profile
                      </Link>
                    </div>

                    {menuItems.length > 0 ? (
                      <div className="border-b border-dark-border py-1">
                        {menuItems.map((item) => (
                          <Link
                            key={item.href}
                            href={item.href}
                            role="menuitem"
                            className="block px-4 py-2 text-sm text-gray-300 transition hover:bg-white/5 hover:text-white"
                          >
                            {item.label}
                          </Link>
                        ))}
                      </div>
                    ) : null}

                    <button
                      type="button"
                      role="menuitem"
                      onClick={() => void signOut()}
                      className="block w-full px-4 py-2.5 text-left text-sm text-gray-400 transition hover:bg-white/5 hover:text-white"
                    >
                      Sign out
                    </button>
                  </div>
                ) : null}
              </div>
            </>
          ) : isLoading ? (
            // Reserve the space so the nav does not jump once the session is known.
            <span className="h-9 w-40" aria-hidden="true" />
          ) : (
            <>
              <Link href="/" className="px-3 py-2 text-gray-300 transition hover:text-white">
                Home
              </Link>
              <Link href="/#features" className="px-3 py-2 text-gray-300 transition hover:text-white">
                Features
              </Link>
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
