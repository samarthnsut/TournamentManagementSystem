'use client'

import { useQuery } from '@tanstack/react-query'
import Card from '../ui/Card'
import StandingsTable from './StandingsTable'
import { ApiError } from '../../lib/api/client'
import { getLeaderboard } from '../../lib/api/fixtures'

/** The organizer's view of the standings. The table itself is shared with the public page. */
export default function LeaderboardPanel({ competitionId }: { competitionId: string }) {
  const leaderboardQuery = useQuery({
    queryKey: ['leaderboard', competitionId],
    queryFn: () => getLeaderboard(competitionId),
    // Nothing played yet answers 409; that is a state, not a failure.
    retry: false,
  })

  const notReady = leaderboardQuery.error instanceof ApiError && leaderboardQuery.error.status === 409
  const board = notReady ? null : leaderboardQuery.data

  return (
    <Card>
      <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-white">Standings</h2>
          {board ? (
            <p className="mt-1 text-sm text-gray-500">
              Updated{' '}
              {new Date(board.computedAt).toLocaleString('en-IN', {
                day: 'numeric',
                month: 'short',
                hour: 'numeric',
                minute: '2-digit',
              })}
            </p>
          ) : null}
        </div>
        {board?.frozen ? (
          <span className="whitespace-nowrap rounded-full border border-green-500/40 bg-green-500/20 px-3 py-1 text-xs font-semibold text-green-300">
            Final
          </span>
        ) : null}
      </div>

      <StandingsTable board={board} isLoading={leaderboardQuery.isLoading} />
    </Card>
  )
}
