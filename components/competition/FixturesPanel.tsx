'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Button from '../ui/Button'
import Card from '../ui/Card'
import StatusBadge from '../ui/StatusBadge'
import ResultEntry from './ResultEntry'
import { ApiError } from '../../lib/api/client'
import {
  generateFixtures,
  getFixtures,
  recordResult,
  regenerateFixtures,
  transitionMatch,
  type Match,
  type ParticipantOutcome,
} from '../../lib/api/fixtures'
import type { Competition } from '../../lib/api/tournaments'

/**
 * The score as it reads on a results sheet — "3 – 1".
 *
 * Only for a head-to-head pairing: dash-joining a whole heat's times produces a string that means
 * nothing to anyone. A field of more than two shows each entrant's own result beside their name
 * instead, which is how a race result is actually read.
 */
function scoreLine(outcomes: ParticipantOutcome[]) {
  if (outcomes.length !== 2) {
    return null
  }
  const measured = outcomes.filter((outcome) => outcome.value !== null)
  if (measured.length !== 2) {
    return null
  }
  return measured.map((outcome) => outcome.value).join(' – ')
}

function formatValue(outcome: ParticipantOutcome) {
  if (outcome.value === null) {
    return outcome.standing === 'NO_CONTEST' ? 'DNF' : null
  }
  return outcome.unit ? `${outcome.value} ${outcome.unit.toLowerCase()}` : String(outcome.value)
}

function MatchRow({
  match,
  competition,
  canRecord,
  canSchedule,
  openMatchId,
  setOpenMatchId,
  onRecord,
  onTransition,
  isSaving,
}: {
  match: Match
  competition: Competition
  canRecord: boolean
  canSchedule: boolean
  openMatchId: string | null
  setOpenMatchId: (id: string | null) => void
  onRecord: (matchId: string, payload: Parameters<typeof recordResult>[1]) => void
  onTransition: (matchId: string, action: 'start' | 'postpone' | 'cancel') => void
  isSaving: boolean
}) {
  const isOpen = openMatchId === match.id
  const isFinished = match.status === 'COMPLETED' || match.status === 'WALKOVER'
  const isTerminal = isFinished || match.status === 'CANCELLED'
  const winnerId = match.result?.winnerParticipantId ?? null

  // A field larger than a pairing reads as a list of results, one per entrant.
  const isField = match.participants.length > 2
  const outcomeOf = (participantId: string) =>
    match.result?.participants.find((outcome) => outcome.participantId === participantId) ?? null

  return (
    <div className="rounded-lg border border-dark-border bg-dark-bg/40 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0 flex-1">
          {/* Participants read as the line-up, with the winner carrying the emphasis. */}
          <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {match.participants.map((participant, index) => (
              <span key={participant.participantId} className="flex items-center gap-2">
                {index > 0 && match.participants.length === 2 ? (
                  <span className="text-xs text-gray-600">v</span>
                ) : null}
                <span
                  className={
                    winnerId === participant.participantId
                      ? 'font-semibold text-white'
                      : 'text-gray-300'
                  }
                >
                  {participant.name ?? 'Unknown entrant'}
                </span>
                {participant.slot && participant.slot.startsWith('LANE') ? (
                  <span className="text-xs text-gray-600">{participant.slot.replace('_', ' ')}</span>
                ) : null}
                {isField && outcomeOf(participant.participantId) ? (
                  <span className="text-sm font-medium text-accent-cyan">
                    {formatValue(outcomeOf(participant.participantId)!)}
                  </span>
                ) : null}
              </span>
            ))}
          </div>

          {match.result ? (
            <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm">
              {scoreLine(match.result.participants) ? (
                <span className="font-semibold text-accent-cyan">
                  {scoreLine(match.result.participants)}
                  {match.result.participants[0]?.unit ? (
                    <span className="ml-1.5 text-xs font-normal text-gray-500">
                      {match.result.participants[0].unit.toLowerCase()}
                    </span>
                  ) : null}
                </span>
              ) : null}
              {match.result.outcome === 'WALKOVER' ? (
                <span className="text-xs text-gray-500">Uncontested</span>
              ) : null}
              {match.result.participants.some((p) => p.standing === 'NO_CONTEST') ? (
                <span className="text-xs text-gray-500">
                  {match.result.participants.filter((p) => p.standing === 'NO_CONTEST').length} did
                  not finish
                </span>
              ) : null}
            </div>
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

      {!isTerminal && (canRecord || canSchedule) ? (
        <div className="mt-3 flex flex-wrap items-center gap-3 border-t border-dark-border pt-3">
          {canRecord && !isOpen ? (
            <button
              type="button"
              onClick={() => setOpenMatchId(match.id)}
              className="rounded-lg border border-accent-purple/40 bg-accent-purple/10 px-4 py-2 text-sm font-semibold text-accent-purple transition hover:border-accent-purple hover:bg-accent-purple/20 focus:outline-none focus:ring-2 focus:ring-accent-purple/40"
            >
              Record result
            </button>
          ) : null}
          {canSchedule && match.status === 'SCHEDULED' ? (
            <button
              type="button"
              onClick={() => onTransition(match.id, 'start')}
              className="rounded-lg border border-dark-border px-4 py-2 text-sm font-medium text-gray-400 transition hover:border-gray-600 hover:bg-white/5 hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-gray-500/30"
            >
              Mark live
            </button>
          ) : null}
          {canSchedule && match.status !== 'POSTPONED' ? (
            <button
              type="button"
              onClick={() => onTransition(match.id, 'postpone')}
              className="rounded-lg border border-dark-border px-4 py-2 text-sm font-medium text-gray-400 transition hover:border-gray-600 hover:bg-white/5 hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-gray-500/30"
            >
              Postpone
            </button>
          ) : null}
        </div>
      ) : null}

      {isOpen ? (
        <ResultEntry
          match={match}
          evaluator={competition.resultEvaluator}
          isSaving={isSaving}
          onCancel={() => setOpenMatchId(null)}
          onSubmit={(payload) => onRecord(match.id, payload)}
        />
      ) : null}
    </div>
  )
}

export default function FixturesPanel({
  competition,
  canGenerate,
  canRecord,
  canSchedule,
  onError,
}: {
  competition: Competition
  canGenerate: boolean
  canRecord: boolean
  canSchedule: boolean
  onError: (cause: unknown) => void
}) {
  const queryClient = useQueryClient()
  const [openMatchId, setOpenMatchId] = useState<string | null>(null)
  const competitionId = competition.id

  const fixturesQuery = useQuery({
    queryKey: ['fixtures', competitionId],
    queryFn: () => getFixtures(competitionId),
    // No draw yet answers 404; that is a state, not a failure.
    retry: false,
  })

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['fixtures', competitionId] })
    await queryClient.invalidateQueries({ queryKey: ['leaderboard', competitionId] })
  }

  const draw = useMutation({
    mutationFn: () => generateFixtures(competitionId),
    onSuccess: refresh,
    onError,
  })

  const redraw = useMutation({
    mutationFn: () => regenerateFixtures(competitionId),
    onSuccess: refresh,
    onError,
  })

  const record = useMutation({
    mutationFn: ({
      matchId,
      payload,
    }: {
      matchId: string
      payload: Parameters<typeof recordResult>[1]
    }) => recordResult(matchId, payload),
    onSuccess: async () => {
      setOpenMatchId(null)
      await refresh()
    },
    onError,
  })

  const transition = useMutation({
    mutationFn: ({ matchId, action }: { matchId: string; action: 'start' | 'postpone' | 'cancel' }) =>
      transitionMatch(matchId, action),
    onSuccess: refresh,
    onError,
  })

  const fixtures = fixturesQuery.data
  const hasDraw = Boolean(fixtures)
  const notFound = fixturesQuery.error instanceof ApiError && fixturesQuery.error.status === 404

  const allMatches = fixtures?.fixtures.flatMap((round) => round.matches) ?? []
  const playedCount = allMatches.filter(
    (match) => match.status === 'COMPLETED' || match.status === 'WALKOVER',
  ).length
  // BR-F-3: the draw is history the moment anything leaves the schedule.
  const canRedraw = allMatches.every(
    (match) => match.status === 'SCHEDULED' || match.status === 'POSTPONED',
  )

  return (
    <Card>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-white">Fixtures</h2>
          <p className="mt-1 text-sm text-gray-500">
            {hasDraw
              ? `${fixtures?.matchCount} ${
                  fixtures?.matchCount === 1 ? 'match' : 'matches'
                } across ${fixtures?.rounds} ${fixtures?.rounds === 1 ? 'round' : 'rounds'} · ${playedCount} played`
              : competition.status === 'CLOSED'
                ? 'Entries are closed. Generate the draw to begin.'
                : 'The draw can be generated once entries are closed.'}
          </p>
        </div>

        {canGenerate && competition.status === 'CLOSED' ? (
          hasDraw ? (
            canRedraw ? (
              <Button
                variant="secondary"
                className="px-4 py-2 text-sm"
                disabled={redraw.isPending}
                onClick={() => {
                  if (
                    window.confirm(
                      'This discards the current draw and pairs everyone again. Continue?',
                    )
                  ) {
                    redraw.mutate()
                  }
                }}
              >
                {redraw.isPending ? 'Redrawing…' : 'Regenerate draw'}
              </Button>
            ) : (
              <span className="whitespace-nowrap rounded-full border border-dark-border bg-white/5 px-3 py-1 text-xs font-semibold text-gray-500">
                Draw locked · matches played
              </span>
            )
          ) : (
            <Button
              className="btn-gradient px-4 py-2 text-sm"
              disabled={draw.isPending}
              onClick={() => draw.mutate()}
            >
              {draw.isPending ? 'Drawing…' : 'Generate draw'}
            </Button>
          )
        ) : null}
      </div>

      {fixturesQuery.isLoading ? (
        <p className="text-sm text-gray-400">Loading fixtures…</p>
      ) : notFound || !fixtures ? (
        <p className="text-sm text-gray-500">
          {competition.status === 'CLOSED'
            ? 'No draw yet.'
            : `Close entries first — the competition is currently ${competition.status.toLowerCase()}.`}
        </p>
      ) : (
        <div className="space-y-6">
          {fixtures.fixtures.map((round) => (
            <div key={round.fixtureId}>
              <h3 className="mb-3 text-sm font-semibold uppercase tracking-wide text-gray-500">
                {round.roundName ?? `Round ${round.round}`}
              </h3>
              <div className="space-y-3">
                {round.matches.map((match) => (
                  <MatchRow
                    key={match.id}
                    match={match}
                    competition={competition}
                    canRecord={canRecord}
                    canSchedule={canSchedule}
                    openMatchId={openMatchId}
                    setOpenMatchId={setOpenMatchId}
                    isSaving={record.isPending}
                    onRecord={(matchId, payload) => record.mutate({ matchId, payload })}
                    onTransition={(matchId, action) => transition.mutate({ matchId, action })}
                  />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}
