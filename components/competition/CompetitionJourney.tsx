'use client'

import Card from '../ui/Card'
import { competitionJourney, nextStepHint } from '../../lib/lifecycle'
import type { CompetitionStatus, TournamentStatus } from '../../lib/api/tournaments'

/**
 * Where this competition is, and what to do next.
 *
 * Two lifecycles running side by side is genuinely confusing, so this says the quiet part out
 * loud: entries are controlled here, by the competition, and the tournament's own status is the
 * event-level phase. That is not a simplification for the UI's benefit — `RegistrationService`
 * checks the competition's status and nothing else.
 */
export default function CompetitionJourney({
  status,
  hasForm,
  tournamentStatus,
}: {
  status: CompetitionStatus
  hasForm: boolean
  tournamentStatus?: TournamentStatus
}) {
  // A drawn competition is IN_PROGRESS or past it — generating the draw is what moves it there —
  // so the status alone answers this without a second request.
  const hasFixtures = status === 'IN_PROGRESS' || status === 'COMPLETED'
  const steps = competitionJourney(status, hasFixtures)

  if (status === 'CANCELLED') {
    return null
  }

  return (
    <Card>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <h2 className="text-lg font-semibold text-white">Where this is up to</h2>
      </div>

      <ol className="flex flex-wrap gap-2">
        {steps.map((step, index) => (
          <li key={step.label} className="flex min-w-[9rem] flex-1 items-start gap-2">
            <span
              aria-hidden="true"
              className={`mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-semibold ${
                step.done
                  ? 'bg-green-500/20 text-green-300'
                  : step.current
                    ? 'bg-accent-purple/25 text-accent-purple'
                    : 'bg-white/5 text-gray-600'
              }`}
            >
              {step.done ? '✓' : index + 1}
            </span>
            <span className="min-w-0">
              <span
                className={`block text-sm font-medium ${
                  step.current ? 'text-white' : step.done ? 'text-gray-300' : 'text-gray-600'
                }`}
              >
                {step.label}
              </span>
              <span className="block text-xs text-gray-600">{step.hint}</span>
            </span>
          </li>
        ))}
      </ol>

      <div className="mt-5 flex items-start gap-2.5 rounded-lg border border-accent-blue/35 bg-accent-blue/10 px-4 py-3 text-sm text-blue-200">
        <svg className="mt-0.5 h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path
            fillRule="evenodd"
            d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm.75-11a.75.75 0 0 0-1.5 0v.5a.75.75 0 0 0 1.5 0V7Zm0 3.25a.75.75 0 0 0-1.5 0v3a.75.75 0 0 0 1.5 0v-3Z"
            clipRule="evenodd"
          />
        </svg>
        <span>{nextStepHint(status, hasFixtures, hasForm)}</span>
      </div>

      {tournamentStatus && tournamentStatus !== 'DRAFT' ? (
        <p className="mt-3 text-xs text-gray-600">
          Entries are controlled here, not by the tournament. The tournament&apos;s own status
          (currently {tournamentStatus.replace(/_/g, ' ').toLowerCase()}) describes the event on its
          public page.
        </p>
      ) : null}
    </Card>
  )
}
