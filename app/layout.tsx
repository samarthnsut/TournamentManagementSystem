import '../styles/globals.css'
import { ReactNode } from 'react'
import { Providers } from '../lib/providers'

export const metadata = {
  title: 'Tekspo Infinity — Sports Event Management',
  description:
    'Revolutionize sports tournament management with multi-tenant portals, registration, fixtures, and live leaderboards.'
}

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-dark-bg text-white antialiased">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
