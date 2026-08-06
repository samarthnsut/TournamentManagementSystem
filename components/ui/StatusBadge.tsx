import React from 'react'

/**
 * Semantic tones from 12_UI_UX_GUIDELINES section 2.4, expressed in the site's dark palette.
 * Meaning is carried by the label; colour only reinforces it, so a badge is never colour-alone.
 */
type Tone = 'neutral' | 'info' | 'success' | 'warning' | 'danger' | 'primary'

const TONE_CLASSES: Record<Tone, string> = {
  neutral: 'bg-gray-700/30 text-gray-300 border-gray-600/40',
  info: 'bg-accent-blue/20 text-accent-blue border-accent-blue/40',
  success: 'bg-green-500/20 text-green-300 border-green-500/40',
  warning: 'bg-amber-500/20 text-amber-300 border-amber-500/40',
  danger: 'bg-red-500/20 text-red-300 border-red-500/40',
  primary: 'bg-accent-purple/20 text-accent-purple border-accent-purple/40',
}

/**
 * The normative enum-to-tone mapping. Keys are shared across entities where the value means the
 * same thing (DRAFT is always neutral, CANCELLED always danger), which is why one table serves
 * tournaments, competitions and registrations alike.
 */
const STATUS_TONES: Record<string, Tone> = {
  // Tournament
  DRAFT: 'neutral',
  PUBLISHED: 'info',
  REGISTRATION_OPEN: 'success',
  REGISTRATION_CLOSED: 'warning',
  IN_PROGRESS: 'primary',
  COMPLETED: 'success',
  CANCELLED: 'danger',
  ARCHIVED: 'neutral',
  // Competition
  OPEN: 'success',
  CLOSED: 'warning',
  // Registration
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
}

function humanize(status: string) {
  const lower = status.replace(/_/g, ' ').toLowerCase()
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}

export default function StatusBadge({ status, className = '' }: { status: string; className?: string }) {
  const tone = STATUS_TONES[status] ?? 'neutral'

  return (
    <span
      className={`inline-flex whitespace-nowrap rounded-full border px-3 py-1 text-xs font-semibold ${TONE_CLASSES[tone]} ${className}`}
    >
      {humanize(status)}
    </span>
  )
}
