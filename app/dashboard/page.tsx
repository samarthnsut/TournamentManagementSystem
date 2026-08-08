'use client'

import Link from 'next/link'
import { useState, useMemo } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Header from '../../components/Header'
import Button from '../../components/ui/Button'
import Card from '../../components/ui/Card'
import Input from '../../components/ui/Input'
import ConfirmDialog from '../../components/ui/ConfirmDialog'
import { TOURNAMENT_TRANSITIONS } from '../../lib/lifecycle'
import { useAuth } from '../../lib/useAuth'
import {
  getTournaments,
  nextAction,
  transitionTournament,
  type Tournament,
  type TournamentAction,
  type TournamentStatus,
} from '../../lib/api/tournaments'

const STATUS_STYLES: Record<TournamentStatus, { border: string; badge: string; label: string }> = {
  DRAFT: {
    border: 'border-gray-600/40',
    badge: 'bg-gray-700/30 text-gray-300 border border-gray-600/40',
    label: 'Draft',
  },
  PUBLISHED: {
    border: 'border-accent-purple/40',
    badge: 'bg-accent-purple/20 text-accent-purple border border-accent-purple/40',
    label: 'Published',
  },
  REGISTRATION_OPEN: {
    border: 'border-accent-cyan/40',
    badge: 'bg-accent-cyan/20 text-accent-cyan border border-accent-cyan/40',
    label: 'Registration open',
  },
  REGISTRATION_CLOSED: {
    border: 'border-accent-cyan/30',
    badge: 'bg-accent-cyan/10 text-accent-cyan border border-accent-cyan/30',
    label: 'Registration closed',
  },
  IN_PROGRESS: {
    border: 'border-accent-orange/40',
    badge: 'bg-accent-orange/20 text-accent-orange border border-accent-orange/40',
    label: 'In progress',
  },
  COMPLETED: {
    border: 'border-green-500/40',
    badge: 'bg-green-500/20 text-green-300 border border-green-500/40',
    label: 'Completed',
  },
  CANCELLED: {
    border: 'border-red-500/40',
    badge: 'bg-red-500/20 text-red-300 border border-red-500/40',
    label: 'Cancelled',
  },
  ARCHIVED: {
    border: 'border-gray-700/40',
    badge: 'bg-gray-800/40 text-gray-400 border border-gray-700/40',
    label: 'Archived',
  },
}

const FILTERS: Array<{ value: string; label: string }> = [
  { value: 'all', label: 'All' },
  { value: 'DRAFT', label: 'Draft' },
  { value: 'PUBLISHED', label: 'Published' },
  { value: 'REGISTRATION_OPEN', label: 'Registration open' },
  { value: 'IN_PROGRESS', label: 'In progress' },
  { value: 'COMPLETED', label: 'Completed' },
]

function formatDateRange(startDate: string | null, endDate: string | null) {
  if (!startDate && !endDate) {
    return 'Dates not set'
  }
  const format = (value: string) =>
    new Date(value).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })
  if (startDate && endDate) {
    return `${format(startDate)} – ${format(endDate)}`
  }
  return format((startDate ?? endDate) as string)
}

export default function DashboardPage() {
  const [searchQuery, setSearchQuery] = useState('')
  const [filterStatus, setFilterStatus] = useState<string>('all')
  const [actionError, setActionError] = useState<string>('')
  // Held until the organizer confirms, so the dialog can explain what the move does.
  const [pendingTransition, setPendingTransition] = useState<{
    tournament: Tournament
    action: TournamentAction
  } | null>(null)
  const queryClient = useQueryClient()
  const { can } = useAuth()

  const { data: tournaments = [], isLoading, isError, error } = useQuery({
    queryKey: ['tournaments'],
    queryFn: getTournaments,
  })

  const transition = useMutation({
    mutationFn: ({ id, action }: { id: string; action: TournamentAction }) => transitionTournament(id, action),
    onSuccess: async () => {
      setActionError('')
      setPendingTransition(null)
      await queryClient.invalidateQueries({ queryKey: ['tournaments'] })
    },
    // Lifecycle rules are enforced server-side, so surface exactly what it said.
    onError: (mutationError) => {
      setPendingTransition(null)
      setActionError(mutationError instanceof Error ? mutationError.message : 'Action failed')
    },
  })

  const filteredTournaments = useMemo(() => {
    const query = searchQuery.trim().toLowerCase()
    return tournaments.filter((tournament) => {
      const matchesSearch =
        query === '' ||
        tournament.name.toLowerCase().includes(query) ||
        tournament.slug.toLowerCase().includes(query)
      const matchesStatus = filterStatus === 'all' || tournament.status === filterStatus
      return matchesSearch && matchesStatus
    })
  }, [tournaments, searchQuery, filterStatus])

  const stats = {
    total: tournaments.length,
    draft: tournaments.filter((t) => t.status === 'DRAFT').length,
    live: tournaments.filter((t) =>
      ['PUBLISHED', 'REGISTRATION_OPEN', 'REGISTRATION_CLOSED', 'IN_PROGRESS'].includes(t.status),
    ).length,
    completed: tournaments.filter((t) => t.status === 'COMPLETED' || t.status === 'ARCHIVED').length,
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-8 sm:py-12">
          <div className="mx-auto max-w-7xl px-6 sm:px-8">
            <div className="flex flex-col gap-2 mb-6">
              <h1 className="text-3xl sm:text-4xl font-bold text-white">Tournament Dashboard</h1>
              <p className="text-gray-400">Manage and monitor all your tournaments in one place</p>
            </div>

            <div className="grid gap-4 grid-cols-2 sm:grid-cols-4">
              <div className="rounded-lg border border-dark-border bg-dark-bg/50 px-4 py-3">
                <p className="text-sm text-gray-400">Total</p>
                <p className="text-2xl font-bold text-white mt-1">{stats.total}</p>
              </div>
              <div className="rounded-lg border border-gray-600/20 bg-gray-700/5 px-4 py-3">
                <p className="text-sm text-gray-300">Draft</p>
                <p className="text-2xl font-bold text-gray-200 mt-1">{stats.draft}</p>
              </div>
              <div className="rounded-lg border border-accent-cyan/20 bg-accent-cyan/5 px-4 py-3">
                <p className="text-sm text-accent-cyan">Live</p>
                <p className="text-2xl font-bold text-accent-cyan mt-1">{stats.live}</p>
              </div>
              <div className="rounded-lg border border-green-500/20 bg-green-500/5 px-4 py-3">
                <p className="text-sm text-green-300">Completed</p>
                <p className="text-2xl font-bold text-green-300 mt-1">{stats.completed}</p>
              </div>
            </div>
          </div>
        </div>

        <div className="mx-auto max-w-7xl px-6 sm:px-8 py-8 sm:py-12">
          <div className="flex flex-col gap-4 sm:gap-6 mb-8">
            <div className="flex flex-col sm:flex-row gap-4 sm:items-center sm:justify-between">
              <Input
                placeholder="Search tournaments by name or slug..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="flex-1"
              />
              {can('tournament:create') ? (
                <Link href="/dashboard/create">
                  <Button className="btn-gradient whitespace-nowrap w-full sm:w-auto">+ Create Tournament</Button>
                </Link>
              ) : null}
            </div>

            <div className="flex flex-wrap gap-2">
              {FILTERS.map((filter) => (
                <button
                  key={filter.value}
                  onClick={() => setFilterStatus(filter.value)}
                  className={`px-4 py-2 rounded-lg border font-medium transition ${
                    filterStatus === filter.value
                      ? 'border-accent-purple bg-accent-purple/20 text-accent-purple'
                      : 'border-dark-border bg-dark-surface text-gray-300 hover:border-gray-600'
                  }`}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            {actionError ? (
              <div className="rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-3 text-sm text-red-300">
                {actionError}
              </div>
            ) : null}
          </div>

          {isLoading ? (
            <div className="rounded-2xl border border-dark-border bg-dark-surface p-12 text-center">
              <p className="text-gray-400">Loading tournaments…</p>
            </div>
          ) : isError ? (
            <div className="rounded-2xl border border-red-500/30 bg-red-500/5 p-12 text-center">
              <p className="text-red-300 mb-2">Could not load tournaments.</p>
              <p className="text-sm text-gray-400">
                {error instanceof Error ? error.message : 'Please check that the backend is running.'}
              </p>
            </div>
          ) : filteredTournaments.length > 0 ? (
            <div className="grid gap-6 md:grid-cols-2 lg:grid-cols-3">
              {filteredTournaments.map((tournament) => {
                const style = STATUS_STYLES[tournament.status]
                const advance = nextAction(tournament.status)
                const isPending = transition.isPending && transition.variables?.id === tournament.id

                return (
                  <Card
                    key={tournament.id}
                    className={`group relative overflow-hidden transition-all duration-300 hover:-translate-y-1 hover:shadow-2xl hover:shadow-accent-purple/20 border-l-4 ${style.border}`}
                  >
                    {/* The whole card is the link. A stretched overlay does that without nesting
                        the action buttons inside an anchor, which would be invalid markup and
                        would swallow their clicks. */}
                    <Link
                      href={`/dashboard/tournament?id=${tournament.id}`}
                      aria-label={`Manage ${tournament.name}`}
                      className="absolute inset-0 z-0 rounded-2xl focus:outline-none focus-visible:ring-2 focus-visible:ring-accent-purple"
                    />
                    <div className="pointer-events-none relative z-10 flex flex-col gap-4">
                      <div className="flex items-start justify-between gap-3">
                        <div className="flex-1">
                          <h3 className="text-lg font-bold text-white transition group-hover:text-accent-purple">
                            {tournament.name}
                          </h3>
                          <p className="mt-1 text-sm text-gray-400">/t/{tournament.slug}</p>
                        </div>
                        <span
                          className={`rounded-full px-3 py-1 text-xs font-semibold whitespace-nowrap ${style.badge}`}
                        >
                          {style.label}
                        </span>
                      </div>

                      <div className="border-t border-dark-border" />

                      <div className="text-sm text-gray-400">
                        <p>{formatDateRange(tournament.startDate, tournament.endDate)}</p>
                        {tournament.description ? (
                          <p className="mt-2 line-clamp-2 text-gray-500">{tournament.description}</p>
                        ) : null}
                      </div>

                      <div className="pointer-events-auto flex gap-2 pt-2">
                        {advance ? (
                          <button
                            onClick={() => setPendingTransition({ tournament, action: advance.action })}
                            disabled={isPending}
                            className="flex-1 rounded-lg border border-accent-orange/30 bg-accent-orange/5 px-3 py-2 text-sm font-medium text-accent-orange hover:bg-accent-orange/10 transition disabled:opacity-50"
                          >
                            {isPending ? 'Working…' : advance.label}
                          </button>
                        ) : null}
                        {tournament.status !== 'DRAFT' ? (
                          <a
                            href={`/t?slug=${tournament.slug}`}
                            target="_blank"
                            rel="noreferrer"
                            className="flex-1 rounded-lg border border-dark-border bg-dark-surface px-3 py-2 text-center text-sm font-medium text-gray-300 hover:border-accent-purple hover:text-accent-purple transition"
                          >
                            Public page
                          </a>
                        ) : null}
                      </div>
                    </div>
                  </Card>
                )
              })}
            </div>
          ) : (
            <div className="rounded-2xl border border-dark-border bg-dark-surface p-12 text-center">
              <div className="text-4xl mb-4">🔍</div>
              <p className="text-gray-400 mb-6">
                {tournaments.length === 0
                  ? 'No tournaments yet.'
                  : 'No tournaments match your search.'}
              </p>
              {can('tournament:create') ? (
                <Link href="/dashboard/create">
                  <Button className="btn-gradient inline-flex">Create Your First Tournament</Button>
                </Link>
              ) : null}
            </div>
          )}
        </div>
      </main>

      <ConfirmDialog
        isOpen={pendingTransition !== null}
        copy={pendingTransition ? TOURNAMENT_TRANSITIONS[pendingTransition.action] : null}
        isWorking={transition.isPending}
        onCancel={() => setPendingTransition(null)}
        onConfirm={() =>
          pendingTransition &&
          transition.mutate({
            id: pendingTransition.tournament.id,
            action: pendingTransition.action,
          })
        }
      />
    </>
  )
}
