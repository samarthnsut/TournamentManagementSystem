'use client'

import type { Leaderboard, LeaderboardEntry } from '../../lib/api/fixtures'

/**
 * Short column headings for the metrics the shipped strategies emit, with the full name on hover.
 *
 * Labels are deliberately sport-neutral: the same `POINTS_TABLE` ranks a football league and a
 * chess Swiss, so "SF" rather than "GF" (ADR-016). Anything not listed falls back to a humanized
 * key, which is what lets a strategy this build has never seen still render a readable table.
 */
const METRIC_LABELS: Record<string, { short: string; full: string }> = {
  played: { short: 'P', full: 'Played' },
  won: { short: 'W', full: 'Won' },
  drawn: { short: 'D', full: 'Drawn' },
  lost: { short: 'L', full: 'Lost' },
  points: { short: 'Pts', full: 'Points' },
  scoreFor: { short: 'SF', full: 'Score for' },
  scoreAgainst: { short: 'SA', full: 'Score against' },
  scoreDifference: { short: 'SD', full: 'Score difference' },
  bestValue: { short: 'Best', full: 'Best result' },
  attempts: { short: 'Att', full: 'Attempts recorded' },
}

/** The columns that carry the actual standing, so they can be weighted differently. */
const PRIMARY_METRICS = new Set(['points', 'bestValue'])

/**
 * Reading order for the metrics we know, because the wire order cannot be trusted: metrics are
 * stored as `jsonb`, and PostgreSQL sorts jsonb keys by length then bytes rather than preserving
 * insertion order. Left to that, a league table reads "W L D P Pts" — which every reader of a
 * league table will misparse. Anything unlisted keeps its first-seen order and follows these.
 */
const COLUMN_ORDER = [
  'played',
  'won',
  'drawn',
  'lost',
  'points',
  'scoreFor',
  'scoreAgainst',
  'scoreDifference',
  'bestValue',
  'attempts',
]

function humanize(key: string) {
  const spaced = key.replace(/([a-z])([A-Z])/g, '$1 $2')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

function formatCell(value: unknown) {
  if (value === null || value === undefined) {
    return '—'
  }
  if (typeof value === 'number' || typeof value === 'string' || typeof value === 'boolean') {
    return String(value)
  }
  return JSON.stringify(value)
}

function columnsOf(entries: LeaderboardEntry[]) {
  const seen: string[] = []
  entries.forEach((entry) => {
    Object.keys(entry.metrics).forEach((key) => {
      // `unit` qualifies another column rather than being one of its own.
      if (key !== 'unit' && !seen.includes(key)) {
        seen.push(key)
      }
    })
  })

  // Known columns in reading order; the rest keep the order they arrived in, after them.
  const known = COLUMN_ORDER.filter((column) => seen.includes(column))
  const unknown = seen.filter((column) => !COLUMN_ORDER.includes(column))
  return [...known, ...unknown]
}

/** Only used when every row agrees, so a mixed board never claims a single unit. */
function commonUnit(entries: LeaderboardEntry[]) {
  const units = new Set(
    entries.map((entry) => entry.metrics.unit).filter((unit) => typeof unit === 'string'),
  )
  return units.size === 1 ? (units.values().next().value as string) : null
}

/**
 * The standings table itself, with no data fetching of its own — the organizer view and the public
 * page read from different endpoints but must render identically, and one component is the only
 * way to guarantee that.
 */
export default function StandingsTable({
  board,
  isLoading,
  emptyMessage = 'Nothing has been played yet. The table appears with the first result.',
}: {
  board: Leaderboard | null | undefined
  isLoading: boolean
  emptyMessage?: string
}) {
  if (isLoading) {
    return <p className="text-sm text-gray-400">Loading standings…</p>
  }

  if (!board) {
    return <p className="text-sm text-gray-500">{emptyMessage}</p>
  }

  const entries = board.entries
  const columns = columnsOf(entries)
  const unit = commonUnit(entries)

  return (
    // Wide tables scroll inside their own box rather than pushing the page sideways.
    <div className="-mx-6 overflow-x-auto px-6">
      <table className="min-w-full text-sm">
        <thead>
          <tr className="border-b border-dark-border text-left text-xs uppercase tracking-wide text-gray-500">
            <th className="py-2 pr-3 font-semibold">#</th>
            <th className="py-2 pr-4 font-semibold">Entrant</th>
            {columns.map((column) => {
              const label = METRIC_LABELS[column]
              const suffix = column === 'bestValue' && unit ? ` (${unit.toLowerCase()})` : ''
              return (
                <th
                  key={column}
                  title={label?.full ?? humanize(column)}
                  className="whitespace-nowrap py-2 pl-3 text-right font-semibold"
                >
                  {(label?.short ?? humanize(column)) + suffix}
                </th>
              )
            })}
          </tr>
        </thead>
        <tbody className="divide-y divide-dark-border">
          {entries.map((entry) => (
            <tr key={entry.participantId} className="transition hover:bg-white/[0.02]">
              <td className="py-3 pr-3 font-semibold text-accent-orange">{entry.rank}</td>
              <td className="py-3 pr-4 text-gray-200">{entry.name ?? 'Unknown entrant'}</td>
              {columns.map((column) => (
                <td
                  key={column}
                  className={`whitespace-nowrap py-3 pl-3 text-right ${
                    PRIMARY_METRICS.has(column) ? 'font-semibold text-white' : 'text-gray-400'
                  }`}
                >
                  {formatCell(entry.metrics[column])}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
