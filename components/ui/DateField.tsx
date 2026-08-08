'use client'

import { useRef } from 'react'

/**
 * A date input that actually looks like one.
 *
 * A bare `type="date"` hides its picker behind a small native icon that is close to invisible on a
 * dark theme, and on Chrome the field is not clickable to open it. Clicking anywhere here opens the
 * calendar via `showPicker()`, with a click on the input itself as the fallback for browsers that
 * do not support it (Safari, and anything older than Chrome 99).
 */
export default function DateField({
  value,
  onChange,
  id,
  min,
  max,
  required,
  className = '',
}: {
  value: string
  onChange: (value: string) => void
  id?: string
  min?: string
  max?: string
  required?: boolean
  className?: string
}) {
  const inputRef = useRef<HTMLInputElement>(null)

  const openPicker = () => {
    const input = inputRef.current
    if (!input) {
      return
    }
    // showPicker throws if the input is not user-editable or the browser refuses; falling back to
    // focus keeps the field usable rather than throwing into the console.
    try {
      ;(input as HTMLInputElement & { showPicker?: () => void }).showPicker?.()
    } catch {
      input.focus()
    }
  }

  return (
    <div
      className={`relative flex items-center rounded-lg border border-dark-border bg-dark-bg transition focus-within:border-accent-purple focus-within:ring-1 focus-within:ring-accent-purple ${className}`}
    >
      <input
        ref={inputRef}
        id={id}
        type="date"
        value={value}
        min={min}
        max={max}
        required={required}
        onChange={(event) => onChange(event.target.value)}
        onClick={openPicker}
        className="w-full bg-transparent px-4 py-3 text-white outline-none [color-scheme:dark] [&::-webkit-calendar-picker-indicator]:hidden"
      />
      <button
        type="button"
        aria-label="Open calendar"
        onClick={openPicker}
        className="absolute right-2 rounded-md p-2 text-gray-400 transition hover:bg-white/5 hover:text-accent-purple focus:outline-none focus:ring-2 focus:ring-accent-purple/40"
      >
        <svg className="h-5 w-5" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true">
          <rect x="2.75" y="4.25" width="14.5" height="13" rx="2" />
          <path d="M2.75 8.25h14.5M6.75 2.75v3M13.25 2.75v3" strokeLinecap="round" />
        </svg>
      </button>
    </div>
  )
}
