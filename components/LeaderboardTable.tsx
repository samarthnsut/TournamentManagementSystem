import React from 'react'

type LeaderboardEntry = {
  position: number
  athlete: string
  sport: string
  score: string
}

type Props = {
  entries: LeaderboardEntry[]
}

export default function LeaderboardTable({ entries }: Props) {
  return (
    <div className="overflow-hidden rounded-lg border border-dark-border bg-dark-surface shadow-lg">
      <table className="min-w-full divide-y divide-dark-border text-sm">
        <thead className="bg-dark-bg text-gray-300">
          <tr>
            <th className="px-4 py-3 text-left font-semibold">Rank</th>
            <th className="px-4 py-3 text-left font-semibold">Athlete</th>
            <th className="px-4 py-3 text-left font-semibold">Sport</th>
            <th className="px-4 py-3 text-left font-semibold">Score</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-dark-border bg-dark-surface">
          {entries.map((entry) => (
            <tr key={entry.position} className="hover:bg-dark-surface/50 transition">
              <td className="px-4 py-4 font-semibold text-accent-orange">{entry.position}</td>
              <td className="px-4 py-4 text-gray-200">{entry.athlete}</td>
              <td className="px-4 py-4 text-gray-300">{entry.sport}</td>
              <td className="px-4 py-4 text-gray-300">{entry.score}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
