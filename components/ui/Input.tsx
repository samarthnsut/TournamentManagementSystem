import React, { InputHTMLAttributes } from 'react'

type Props = InputHTMLAttributes<HTMLInputElement>

export default function Input({ className = '', ...props }: Props) {
  return (
    <input
      className={`w-full rounded-lg border border-dark-border bg-dark-surface px-4 py-3 text-white outline-none transition focus:border-accent-blue focus:ring-2 focus:ring-accent-blue/20 placeholder:text-gray-500 ${className}`}
      {...props}
    />
  )
}
