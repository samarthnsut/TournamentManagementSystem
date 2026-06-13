import Link from 'next/link'
import { ReactNode } from 'react'

type AuthLinkProps = {
  href: string
  children: ReactNode
}

export default function AuthLink({ href, children }: AuthLinkProps) {
  return (
    <Link href={href} className="font-medium text-accent-blue transition hover:text-accent-pink">
      {children}
    </Link>
  )
}
