'use client'

import { useQuery } from '@tanstack/react-query'
import Card from '../ui/Card'
import StatusBadge from '../ui/StatusBadge'
import Podium from './Podium'
import StandingsTable from './StandingsTable'
import { ApiError } from '../../lib/api/client'
import {
  getPublicFixtures,
  getPublicLeaderboard,
  type PublicMatch,
} from '../../lib/api/fixtures'

function outcomeOf(match: PublicMatch, participantId: string) {
  return match.result?.participants.find((outcome) => outcome.participantId === participantId) ?? null
}

function formatValue(outcome: NonNullable<PublicMatch['result']>['participants'][number]) {
  if (outcome.value === null) {
    return outcome.standing === 'NO_CONTEST' ? 'DNF' : null
  }
  return outcome.unit ? `${outcome.value} ${outcome.unit.toLowerCase()}` : String(outcome.value)
}

/** The read-only results a visitor sees: the same numbers the organizer has, none of the controls. */
export default function PublicCompetitionResults({
  slug,
  competitionId,
}: {
  slug: string
  competitionId: string
}) {
  const boardQuery = useQuery({
    queryKey: ['public-leaderboard', slug, competitionId],
    queryFn: () => getPublicLeaderboard(slug, competitionId),
    // 409 before anything is played, 404 before a draw exists. Both are states, not failures.
    retry: false,
  })

  const fixturesQuery = useQuery({
    queryKey: ['public-fixtures', slug, competitionId],
    queryFn: () => getPublicFixtures(slug, competitionId),
    retry: false,
  })

  const boardUnavailable = boardQuery.error instanceof ApiError
  const board = boardUnavailable ? null : boardQuery.data
  const fixtures = fixturesQuery.error instanceof ApiError ? null : fixturesQuery.data

  return (
    <div className="mt-6 space-y-6">
      <Card>
        <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
          <h3 className="text-lg font-semibold text-white">Standings</h3>
          {board?.frozen ? (
            <span className="whitespace-nowrap rounded-full border border-green-500/40 bg-green-500/20 px-3 py-1 text-xs font-semibold text-green-300">
              Final
            </span>
          ) : null}
        </div>
        {board ? <Podium board={board} /> : null}
        <StandingsTable
          board={board}
          isLoading={boardQuery.isLoading}
          emptyMessage="Standings appear once the first result is in."
        />
      </Card>

      <Card>
        <h3 className="mb-5 text-lg font-semibold text-white">Fixtures</h3>

        {fixturesQuery.isLoading ? (
          <p className="text-sm text-gray-400">Loading fixtures…</p>
        ) : !fixtures ? (
          <p className="text-sm text-gray-500">The draw has not been published yet.</p>
        ) : (
          <div className="space-y-6">
            {fixtures.fixtures.map((round) => (
              <div key={round.round}>
                <h4 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
                  {round.roundName ?? `Round ${round.round}`}
                </h4>
                <div className="space-y-3">
                  {round.matches.map((match) => {
                    const isField = match.participants.length > 2
                    return (
                      <div
                        key={match.id}
                        className="flex flex-wrap items-start justify-between gap-3 rounded-lg border border-dark-border bg-dark-bg/40 p-4"
                      >
                        <div className="min-w-0 flex-1">
                          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                            {match.participants.map((participant, index) => {
                              const outcome = outcomeOf(match, participant.participantId)
                              return (
                                <span
                                  key={participant.participantId}
                                  className="flex items-center gap-2"
                                >
                                  {index > 0 && !isField ? (
                                    <span className="text-xs text-gray-600">v</span>
                                  ) : null}
                                  <span
                                    className={
                                      match.result?.winnerParticipantId === participant.participantId
                                        ? 'font-semibold text-white'
                                        : 'text-gray-300'
                                    }
                                  >
                                    {participant.name ?? 'Unknown entrant'}
                                  </span>
                                  {participant.slot?.startsWith('LANE') ? (
                                    <span className="text-xs text-gray-600">
                                      {participant.slot.replace('_', ' ')}
                                    </span>
                                  ) : null}
                                  {isField && outcome ? (
                                    <span className="text-sm font-medium text-accent-cyan">
                                      {formatValue(outcome)}
                                    </span>
                                  ) : null}
                                </span>
                              )
                            })}
                          </div>

                          {!isField && match.result ? (
                            <p className="mt-2 font-semibold text-accent-cyan">
                              {match.result.participants.every((p) => p.value !== null)
                                ? match.result.participants.map((p) => p.value).join(' – ')
                                : 'Uncontested'}
                            </p>
                          ) : null}

                          {match.scheduledAt ? (
                            <p className="mt-1 text-xs text-gray-600">
                              {new Date(match.scheduledAt).toLocaleString('en-IN', {
                                day: 'numeric',
                                month: 'short',
                                hour: 'numeric',
                                minute: '2-digit',
                              })}
                            </p>
                          ) : null}
                        </div>

                        <StatusBadge status={match.status} />
                      </div>
                    )
                  })}
                </div>
              </div>
            ))}
          </div>
        )}
      </Card>
    </div>
  )
}
