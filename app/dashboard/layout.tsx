'use client'

import { ReactNode } from 'react'
import RequireAuth from '../../components/RequireAuth'

/** Everything under /dashboard needs a signed-in user. */
export default function DashboardLayout({ children }: { children: ReactNode }) {
  return <RequireAuth>{children}</RequireAuth>
}
