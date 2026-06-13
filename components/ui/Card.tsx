import React, { ReactNode } from 'react'

type CardProps = {
  children: ReactNode
  className?: string
}

export default function Card({ children, className = '' }: CardProps) {
  return (
    <div className={`rounded-2xl border border-dark-border bg-dark-surface p-6 shadow-lg ${className}`}>
      {children}
    </div>
  )
}
