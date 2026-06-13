import React, { ButtonHTMLAttributes } from 'react'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'ghost' }

export default function Button({ variant = 'primary', className = '', ...props }: Props) {
  const base = 'rounded-full font-medium transition-all duration-300 '
  const variants = {
    primary: 'bg-gradient-brand text-white hover:shadow-lg hover:shadow-accent-purple/25 hover:scale-[1.02] px-6 py-3',
    secondary:
      'border border-white/15 bg-dark-surface/50 text-gray-200 backdrop-blur-sm hover:border-accent-purple/50 hover:text-white px-6 py-3',
    ghost: 'text-gray-400 hover:text-white px-4 py-2'
  }

  return <button className={`${base} ${variants[variant]} ${className}`} {...props} />
}
