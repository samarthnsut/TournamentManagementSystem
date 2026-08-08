'use client'

import { useEffect, useState } from 'react'
import Button from './Button'
import Modal from './Modal'
import type { TransitionCopy } from '../../lib/lifecycle'

/**
 * A confirmation that explains the consequence rather than asking "are you sure?".
 *
 * `window.confirm` was doing this job and could not say what a move unlocks or locks — which is
 * most of what an organizer needs to know before pressing an irreversible button. `window.prompt`
 * was collecting rejection reasons, with no way to mark one required or to explain why it is asked
 * for; `reasonLabel` replaces it.
 */
export default function ConfirmDialog({
  copy,
  isOpen,
  isWorking,
  reasonLabel,
  reasonHint,
  onConfirm,
  onCancel,
}: {
  copy: TransitionCopy | null
  isOpen: boolean
  isWorking?: boolean
  /** When set, the dialog collects a mandatory free-text reason and passes it to onConfirm. */
  reasonLabel?: string
  reasonHint?: string
  onConfirm: (reason?: string) => void
  onCancel: () => void
}) {
  const [reason, setReason] = useState('')

  // A reason typed for one entry must not be waiting in the box for the next.
  useEffect(() => {
    if (!isOpen) {
      setReason('')
    }
  }, [isOpen])

  if (!copy) {
    return null
  }

  const needsReason = Boolean(reasonLabel)
  const canConfirm = !isWorking && (!needsReason || reason.trim().length > 0)

  return (
    <Modal isOpen={isOpen} title={copy.title} onClose={onCancel} size="sm">
      <p className="text-sm text-gray-300">{copy.summary}</p>

      {copy.unlocks ? (
        <p className="mt-4 flex gap-2 text-sm text-green-300">
          <span aria-hidden="true">→</span>
          <span>{copy.unlocks}</span>
        </p>
      ) : null}

      {copy.locks ? (
        <p className="mt-2 flex gap-2 text-sm text-amber-300">
          <span aria-hidden="true">!</span>
          <span>{copy.locks}</span>
        </p>
      ) : null}

      {copy.irreversible ? (
        <p className="mt-4 rounded-lg border border-red-500/30 bg-red-500/5 px-3 py-2 text-sm text-red-300">
          This cannot be undone.
        </p>
      ) : null}

      {needsReason ? (
        <div className="mt-5">
          <label htmlFor="confirm-reason" className="mb-2 block text-sm font-medium text-gray-300">
            {reasonLabel}
          </label>
          <textarea
            id="confirm-reason"
            rows={3}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            className="w-full rounded-lg border border-dark-border bg-dark-bg px-4 py-3 text-sm text-white focus:border-accent-purple focus:outline-none focus:ring-1 focus:ring-accent-purple"
          />
          {reasonHint ? <p className="mt-2 text-xs text-gray-600">{reasonHint}</p> : null}
        </div>
      ) : null}

      <div className="mt-6 flex flex-wrap justify-end gap-3">
        <Button variant="secondary" className="px-5 py-2 text-sm" onClick={onCancel}>
          Cancel
        </Button>
        <Button
          className={`px-5 py-2 text-sm ${copy.irreversible ? '' : 'btn-gradient'}`}
          style={copy.irreversible ? { background: 'rgb(220 38 38)', color: 'white' } : undefined}
          disabled={!canConfirm}
          onClick={() => onConfirm(needsReason ? reason.trim() : undefined)}
        >
          {isWorking ? 'Working…' : copy.confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}
