'use client'

import { useEffect, useRef, type ReactNode } from 'react'

/**
 * The dialog shell every modal in the app sits in.
 *
 * It owns the parts that are easy to leave out and annoying to hit: Escape closes it, a click on
 * the backdrop closes it, the page behind stops scrolling, and focus moves into the dialog so a
 * keyboard user is not left tabbing around the page underneath.
 */
export default function Modal({
  isOpen,
  title,
  description,
  onClose,
  children,
  footer,
  size = 'md',
  error,
}: {
  isOpen: boolean
  title: string
  description?: string
  onClose: () => void
  children: ReactNode
  footer?: ReactNode
  size?: 'sm' | 'md' | 'lg'
  /** Shown inside the dialog. An error rendered on the page behind is an error nobody reads. */
  error?: string
}) {
  const panelRef = useRef<HTMLDivElement>(null)

  // Callers pass `onClose` as an inline arrow, so its identity changes on every parent render. Held
  // in a ref, the Escape listener still calls the latest one without the effects below re-running
  // — which they did on every keystroke, stealing focus back to the first field mid-word.
  const onCloseRef = useRef(onClose)
  useEffect(() => {
    onCloseRef.current = onClose
  })

  useEffect(() => {
    if (!isOpen) {
      return
    }

    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        onCloseRef.current()
      }
    }
    window.addEventListener('keydown', onKey)

    // A modal over a scrolling page scrolls the page instead of itself; lock it while open.
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    return () => {
      window.removeEventListener('keydown', onKey)
      document.body.style.overflow = previousOverflow
    }
  }, [isOpen])

  // Autofocus belongs to opening, not to rendering: it must fire once per open, never again.
  useEffect(() => {
    if (!isOpen) {
      return
    }
    const focusable = panelRef.current?.querySelector<HTMLElement>(
      'input:not([type="hidden"]), textarea, select, button, [tabindex]:not([tabindex="-1"])',
    )
    focusable?.focus()
  }, [isOpen])

  if (!isOpen) {
    return null
  }

  const widths = { sm: 'max-w-md', md: 'max-w-2xl', lg: 'max-w-4xl' }

  return (
    <div
      className="fixed inset-0 z-[100] flex items-start justify-center overflow-y-auto bg-black/70 p-4 py-10 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-labelledby="modal-title"
      onClick={onClose}
    >
      <div
        ref={panelRef}
        className={`w-full ${widths[size]} rounded-2xl border border-dark-border bg-dark-surface shadow-2xl`}
        // A click inside must not fall through to the backdrop's dismiss.
        onClick={(event) => event.stopPropagation()}
      >
        <div className="flex items-start justify-between gap-4 border-b border-dark-border px-6 py-5">
          <div className="min-w-0">
            <h2 id="modal-title" className="text-lg font-semibold text-white">
              {title}
            </h2>
            {description ? <p className="mt-1 text-sm text-gray-500">{description}</p> : null}
          </div>
          <button
            type="button"
            aria-label="Close"
            onClick={onClose}
            className="shrink-0 rounded-lg p-1.5 text-gray-500 transition hover:bg-white/5 hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-accent-purple/40"
          >
            <svg className="h-5 w-5" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
              <path d="M5 5l10 10M15 5L5 15" strokeLinecap="round" />
            </svg>
          </button>
        </div>

        <div className="px-6 py-5">
          {error ? (
            <div
              role="alert"
              className="mb-5 flex items-start gap-2.5 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
            >
              <svg className="mt-0.5 h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path
                  fillRule="evenodd"
                  d="M8.49 3.17a1.75 1.75 0 0 1 3.02 0l6.28 10.8A1.75 1.75 0 0 1 16.28 16.6H3.72a1.75 1.75 0 0 1-1.51-2.63l6.28-10.8ZM10 7a.75.75 0 0 1 .75.75v3a.75.75 0 0 1-1.5 0v-3A.75.75 0 0 1 10 7Zm0 7a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"
                  clipRule="evenodd"
                />
              </svg>
              <span>{error}</span>
            </div>
          ) : null}
          {children}
        </div>

        {footer ? (
          <div className="flex flex-wrap justify-end gap-3 border-t border-dark-border px-6 py-4">
            {footer}
          </div>
        ) : null}
      </div>
    </div>
  )
}
