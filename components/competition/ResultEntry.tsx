'use client'

import { useState } from 'react'
import Button from '../ui/Button'
import type { Match, ResultEvaluatorKey, ScorePayload } from '../../lib/api/fixtures'

/**
 * What the value in the box means, per evaluator. This is the one place the UI is allowed to speak
 * a sport's language, and it does so keyed on the strategy rather than on the sport — an evaluator
 * this map has never seen still renders, just with a neutral label.
 */
const VALUE_LABEL: Record<ResultEvaluatorKey, string> = {
  POINTS: 'Goals',
  SCORE: 'Score',
  WIN_LOSS: 'Score',
  TIME: 'Time (seconds)',
  DISTANCE: 'Distance (metres)',
}

/**
 * Scores are whole; measurements are not constrained at all.
 *
 * A fixed decimal step looks tidier but browsers enforce it: timing gear reporting 10.9994 would
 * fail step validation and the form would refuse to submit, with the explanation buried in a native
 * tooltip. The evaluator already rounds to the sport's configured precision, so imposing
 * granularity here can only reject values the backend would have accepted.
 */
const VALUE_STEP: Record<ResultEvaluatorKey, string> = {
  POINTS: '1',
  SCORE: '1',
  WIN_LOSS: '1',
  TIME: 'any',
  DISTANCE: 'any',
}

const MEASURED: ResultEvaluatorKey[] = ['TIME', 'DISTANCE']

export default function ResultEntry({
  match,
  evaluator,
  isSaving,
  error,
  onCancel,
  onSubmit,
}: {
  match: Match
  evaluator: ResultEvaluatorKey | null
  isSaving: boolean
  /** Shown against the form itself; a banner at the top of the page is off-screen from here. */
  error?: string
  onCancel: () => void
  onSubmit: (payload: {
    outcome: 'COMPLETED' | 'WALKOVER'
    scores?: ScorePayload[]
    winnerParticipantId?: string
    version: number
  }) => void
}) {
  const [isWalkover, setIsWalkover] = useState(false)
  const [winnerId, setWinnerId] = useState('')
  const [values, setValues] = useState<Record<string, string>>({})

  const key = evaluator ?? 'SCORE'
  const label = VALUE_LABEL[key] ?? 'Value'
  const step = VALUE_STEP[key] ?? '1'
  const isMeasured = MEASURED.includes(key)

  const submit = (event: React.FormEvent) => {
    event.preventDefault()

    if (isWalkover) {
      if (!winnerId) {
        return
      }
      onSubmit({ outcome: 'WALKOVER', winnerParticipantId: winnerId, version: match.version })
      return
    }

    const scores: ScorePayload[] = match.participants.map((participant) => {
      const raw = values[participant.participantId]
      // A blank box in a measured event is how "did not finish" is recorded; in a scored event
      // it is simply not filled in yet, and the API will say so.
      const value = raw === undefined || raw.trim() === '' ? null : Number(raw)
      return {
        participantId: participant.participantId,
        value,
        ...(isMeasured ? { unit: key === 'TIME' ? 'SECONDS' : 'METRES' } : {}),
      }
    })

    onSubmit({ outcome: 'COMPLETED', scores, version: match.version })
  }

  return (
    <form onSubmit={submit} className="mt-3 space-y-4 border-t border-dark-border pt-4">
      {error ? (
        <div
          role="alert"
          className="flex items-start gap-2.5 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-3 text-sm text-red-200"
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
      <div className="flex flex-wrap items-center gap-4">
        <label className="flex items-center gap-2 text-sm text-gray-300">
          <input
            type="radio"
            checked={!isWalkover}
            onChange={() => setIsWalkover(false)}
            className="accent-accent-purple"
          />
          Played
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-300">
          <input
            type="radio"
            checked={isWalkover}
            onChange={() => setIsWalkover(true)}
            className="accent-accent-purple"
          />
          Walkover
        </label>
      </div>

      {isWalkover ? (
        <div>
          <label className="mb-2 block text-sm font-medium text-gray-300">
            Who takes it uncontested?
          </label>
          <div className="space-y-2">
            {match.participants.map((participant) => (
              <label
                key={participant.participantId}
                className="flex items-center gap-2 text-sm text-gray-300"
              >
                <input
                  type="radio"
                  name={`walkover-${match.id}`}
                  checked={winnerId === participant.participantId}
                  onChange={() => setWinnerId(participant.participantId)}
                  className="accent-accent-purple"
                />
                {participant.name ?? 'Unknown entrant'}
              </label>
            ))}
          </div>
        </div>
      ) : (
        <div className="space-y-2">
          <p className="text-sm font-medium text-gray-300">{label}</p>
          {match.participants.map((participant) => (
            <div key={participant.participantId} className="flex items-center gap-3">
              <span className="min-w-0 flex-1 truncate text-sm text-gray-300">
                {participant.name ?? 'Unknown entrant'}
                {participant.slot ? (
                  <span className="ml-2 text-xs text-gray-600">{participant.slot}</span>
                ) : null}
              </span>
              <input
                type="number"
                step={step}
                min="0"
                inputMode="decimal"
                value={values[participant.participantId] ?? ''}
                onChange={(event) =>
                  setValues({ ...values, [participant.participantId]: event.target.value })
                }
                className="w-28 rounded-lg border border-dark-border bg-dark-bg px-3 py-2 text-sm text-white focus:border-accent-purple focus:outline-none focus:ring-1 focus:ring-accent-purple"
                placeholder={isMeasured ? '—' : '0'}
              />
            </div>
          ))}
          {isMeasured ? (
            <p className="pt-1 text-xs text-gray-600">
              Leave a box empty for an entrant who did not finish. They stay on the board, below
              everyone who did.
            </p>
          ) : null}
        </div>
      )}

      <div className="flex flex-wrap gap-3">
        <Button
          type="submit"
          className="btn-gradient px-5 py-2 text-sm"
          disabled={isSaving || (isWalkover && !winnerId)}
        >
          {isSaving ? 'Saving…' : 'Save result'}
        </Button>
        <Button type="button" variant="secondary" className="px-5 py-2 text-sm" onClick={onCancel}>
          Cancel
        </Button>
      </div>
    </form>
  )
}
