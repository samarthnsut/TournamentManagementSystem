import React from 'react'

type BadgeProps = {
  children: React.ReactNode
  variant?: 'primary' | 'secondary'
}

export default function Badge({ children, variant = 'primary' }: BadgeProps) {
  const base = 'inline-flex rounded-full px-3 py-1 text-sm font-semibold'
  const color =
    variant === 'primary'
      ? 'bg-gradient-cta text-white'
      : 'bg-dark-border text-gray-300'

  return <span className={`${base} ${color}`}>{children}</span>
}
