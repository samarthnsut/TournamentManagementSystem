import React from 'react'

type Tournament = {
  id: string
  name: string
  location: string
  startDate: string
  endDate: string
  description?: string
}

export default function TournamentCard({ tournament }: { tournament: Tournament }) {
  return (
    <div className="bg-white shadow rounded p-6">
      <h3 className="text-xl font-semibold">{tournament.name}</h3>
      <p className="text-sm text-gray-600">{tournament.location}</p>
      <p className="mt-2 text-sm text-gray-700">{tournament.description}</p>

      <div className="mt-4 text-sm text-gray-500">
        <div>
          <strong>Dates:</strong> {tournament.startDate} — {tournament.endDate}
        </div>
      </div>
    </div>
  )
}
