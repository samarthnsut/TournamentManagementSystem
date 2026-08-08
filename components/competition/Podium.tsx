'use client'

import type { Leaderboard } from '../../lib/api/fixtures'

const PLACES = [
  { medal: '🥇', label: 'Champion', ring: 'border-amber-400/50 bg-amber-400/10' },
  { medal: '🥈', label: 'Runner-up', ring: 'border-gray-400/40 bg-gray-400/10' },
  { medal: '🥉', label: 'Third', ring: 'border-orange-700/50 bg-orange-700/10' },
]

/**
 * Who actually won.
 *
 * A frozen table with the winner on the top row is technically complete and practically useless —
 * the one thing everybody opens the page for should not need reading a table to find. Shown only
 * once a competition is finished, because a leader halfway through a league is not a winner.
 *
 * Ranks are shared on a tie (1, 1, 3), so this reads the ranks rather than the first three rows:
 * two entrants tied for first are both champions and there is no runner-up.
 */
export default function Podium({ board }: { board: Leaderboard }) {
  if (!board.frozen || board.entries.length === 0) {
    return null
  }

  const byRank = [1, 2, 3].map((rank) => board.entries.filter((entry) => entry.rank === rank))
  if (byRank[0].length === 0) {
    return null
  }

  return (
    <div className="mb-6 rounded-xl border border-dark-border bg-gradient-to-b from-amber-500/[0.07] to-transparent p-5">
      <p className="mb-4 text-xs font-semibold uppercase tracking-wider text-gray-500">Final result</p>

      <div className="flex flex-wrap gap-3">
        {byRank.map((entrants, index) =>
          entrants.length === 0 ? null : (
            <div
              key={PLACES[index].label}
              className={`flex min-w-[12rem] flex-1 items-center gap-3 rounded-lg border px-4 py-3 ${PLACES[index].ring}`}
            >
              <span aria-hidden="true" className="text-2xl">
                {PLACES[index].medal}
              </span>
              <div className="min-w-0">
                <p className="text-xs uppercase tracking-wide text-gray-500">
                  {PLACES[index].label}
                  {entrants.length > 1 ? ` · tied` : ''}
                </p>
                {entrants.map((entrant) => (
                  <p key={entrant.participantId} className="truncate font-semibold text-white">
                    {entrant.name ?? 'Unknown entrant'}
                  </p>
                ))}
              </div>
            </div>
          ),
        )}
      </div>
    </div>
  )
}
