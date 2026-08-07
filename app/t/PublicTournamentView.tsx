'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import Header from '../../components/Header'
import Card from '../../components/ui/Card'
import PublicCompetitionResults from '../../components/competition/PublicCompetitionResults'
import { getPublicTournament, type CompetitionStatus } from '../../lib/api/tournaments'

const COMPETITION_BADGE: Record<CompetitionStatus, string> = {
  DRAFT: 'bg-gray-700/30 text-gray-300 border border-gray-600/40',
  OPEN: 'bg-accent-cyan/20 text-accent-cyan border border-accent-cyan/40',
  CLOSED: 'bg-accent-purple/20 text-accent-purple border border-accent-purple/40',
  IN_PROGRESS: 'bg-accent-orange/20 text-accent-orange border border-accent-orange/40',
  COMPLETED: 'bg-green-500/20 text-green-300 border border-green-500/40',
  CANCELLED: 'bg-red-500/20 text-red-300 border border-red-500/40',
}

function formatDateRange(startDate: string | null, endDate: string | null) {
  if (!startDate && !endDate) {
    return null
  }
  const format = (value: string) =>
    new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'long', year: 'numeric' })
  if (startDate && endDate) {
    return `${format(startDate)} – ${format(endDate)}`
  }
  return format((startDate ?? endDate) as string)
}

/**
 * The anonymous tournament page. It deliberately makes no authenticated calls, so it works for a
 * visitor who has never signed in — and an unpublished tournament simply reads as not found.
 */
export default function PublicTournamentView({ slug }: { slug: string }) {
  // Results are shown for one competition at a time: a tournament can carry a dozen, and loading
  // every draw and board at once would make the page slower the more successful the event is.
  const [openCompetitionId, setOpenCompetitionId] = useState<string | null>(null)

  const { data: tournament, isLoading, isError } = useQuery({
    queryKey: ['public-tournament', slug],
    queryFn: () => getPublicTournament(slug),
    enabled: Boolean(slug),
    retry: false,
  })

  const dateRange = tournament ? formatDateRange(tournament.startDate, tournament.endDate) : null

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        {isLoading ? (
          <div className="mx-auto max-w-5xl px-6 sm:px-8 py-20 text-center">
            <p className="text-gray-400">Loading…</p>
          </div>
        ) : isError || !tournament ? (
          <div className="mx-auto max-w-5xl px-6 sm:px-8 py-20 text-center">
            <div className="text-5xl mb-4">🏟️</div>
            <h1 className="text-2xl font-bold text-white mb-2">Tournament not found</h1>
            <p className="text-gray-400">
              This tournament does not exist, or it has not been published yet.
            </p>
          </div>
        ) : (
          <>
            <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-12 sm:py-16">
              <div className="mx-auto max-w-5xl px-6 sm:px-8">
                {tournament.organizer ? (
                  <p className="text-sm uppercase tracking-wide text-accent-cyan mb-3">
                    {tournament.organizer.name}
                  </p>
                ) : null}
                <h1 className="text-3xl sm:text-5xl font-bold text-white mb-4">{tournament.name}</h1>
                {dateRange ? <p className="text-lg text-gray-300">{dateRange}</p> : null}
                {tournament.description ? (
                  <p className="mt-4 max-w-2xl text-gray-400">{tournament.description}</p>
                ) : null}
              </div>
            </div>

            <div className="mx-auto max-w-5xl px-6 sm:px-8 py-10 sm:py-14">
              <h2 className="text-xl font-semibold text-white mb-6">
                Competitions
                <span className="ml-2 text-sm font-normal text-gray-500">
                  ({tournament.competitions.length})
                </span>
              </h2>

              {tournament.competitions.length === 0 ? (
                <div className="rounded-2xl border border-dark-border bg-dark-surface p-10 text-center">
                  <p className="text-gray-400">Competitions have not been announced yet.</p>
                </div>
              ) : (
                <div className="space-y-4">
                  {tournament.competitions.map((competition) => {
                    const isOpen = openCompetitionId === competition.id
                    // A competition nobody has drawn yet has nothing to show, so it does not
                    // pretend to be expandable.
                    const hasResults =
                      competition.status !== 'DRAFT' && competition.status !== 'OPEN'

                    return (
                      <Card key={competition.id}>
                        <div className="flex flex-wrap items-start justify-between gap-3">
                          <div>
                            <h3 className="text-lg font-semibold text-white">{competition.name}</h3>
                            {competition.sportCode ? (
                              <p className="mt-1 text-sm text-gray-500">{competition.sportCode}</p>
                            ) : null}
                          </div>
                          <div className="flex flex-wrap items-center gap-3">
                            <span
                              className={`rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap ${
                                COMPETITION_BADGE[competition.status]
                              }`}
                            >
                              {competition.status.replace('_', ' ').toLowerCase()}
                            </span>
                            {hasResults ? (
                              <button
                                type="button"
                                aria-expanded={isOpen}
                                onClick={() => setOpenCompetitionId(isOpen ? null : competition.id)}
                                className="rounded-lg border border-accent-cyan/40 bg-accent-cyan/10 px-4 py-2 text-sm font-semibold text-accent-cyan transition hover:border-accent-cyan hover:bg-accent-cyan/20 focus:outline-none focus:ring-2 focus:ring-accent-cyan/40"
                              >
                                {isOpen ? 'Hide results' : 'Results & fixtures'}
                              </button>
                            ) : null}
                          </div>
                        </div>

                        {isOpen ? (
                          <PublicCompetitionResults slug={slug} competitionId={competition.id} />
                        ) : null}
                      </Card>
                    )
                  })}
                </div>
              )}
            </div>
          </>
        )}
      </main>
    </>
  )
}
