'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { useAuth } from '../../lib/useAuth'

/**
 * The administrative corner of the product. Each tab is hidden when the caller cannot use it —
 * `can()` falls open when permissions are unknown, so this narrows the menu without ever being the
 * security boundary (the API is).
 */
const TABS = [
  { href: '/dashboard/settings/users', label: 'People & roles', permission: 'user:read' },
  { href: '/dashboard/settings/venues', label: 'Venues', permission: 'venue:read' },
  { href: '/dashboard/settings/sports', label: 'Sport configurations', permission: 'sport-config:read' },
  { href: '/dashboard/settings/workflows', label: 'Approval workflows', permission: 'approval:read' },
  { href: '/dashboard/audit', label: 'Audit trail', permission: 'audit:read' },
]

export default function SettingsNav() {
  const pathname = usePathname()
  const { can } = useAuth()

  return (
    <nav className="mb-8 flex flex-wrap gap-2 border-b border-dark-border pb-4">
      {TABS.filter((tab) => can(tab.permission)).map((tab) => {
        const isActive = pathname === tab.href
        return (
          <Link
            key={tab.href}
            href={tab.href}
            className={`rounded-lg px-4 py-2 text-sm font-medium transition ${
              isActive
                ? 'bg-accent-purple/20 text-accent-purple'
                : 'text-gray-400 hover:bg-white/5 hover:text-gray-200'
            }`}
          >
            {tab.label}
          </Link>
        )
      })}
    </nav>
  )
}
