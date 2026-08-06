'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Header from '../../../components/Header'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import Input from '../../../components/ui/Input'
import StatusBadge from '../../../components/ui/StatusBadge'
import Select from '../../../components/ui/Select'
import { useAuth } from '../../../lib/useAuth'
import { buildConfigFor, hasPresetFor } from '../../../lib/sportPresets'
import {
  createCompetition,
  createSportConfiguration,
  getCompetitions,
  getSportConfigurations,
  getSports,
  getTournament,
  nextAction,
  nextCompetitionAction,
  setApprovalPolicy,
  transitionCompetition,
  type ApprovalPolicy,
  transitionTournament,
  type CompetitionAction,
  type TournamentAction,
} from '../../../lib/api/tournaments'

const APPROVAL_CHOICES: Array<{ value: ApprovalPolicy; label: string; description: string }> = [
  {
    value: 'DIRECT_SINGLE_APPROVAL',
    label: 'Review each entry',
    description: 'Entries arrive as Pending and an organizer approves or rejects them.',
  },
  {
    value: 'AUTO_APPROVE',
    label: 'Accept automatically',
    description: 'Entries are Approved on submission. Nobody has to act.',
  },
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

export default function TournamentDetailView({ tournamentId }: { tournamentId: string }) {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const [actionError, setActionError] = useState('')
  const [competitionName, setCompetitionName] = useState('')
  const [sportCode, setSportCode] = useState('')
  const [maxRegistrations, setMaxRegistrations] = useState('')

  const tournamentQuery = useQuery({
    queryKey: ['tournament', tournamentId],
    queryFn: () => getTournament(tournamentId),
    enabled: Boolean(tournamentId),
    retry: false,
  })

  const competitionsQuery = useQuery({
    queryKey: ['competitions', tournamentId],
    queryFn: () => getCompetitions(tournamentId),
    enabled: Boolean(tournamentId),
  })

  const sportsQuery = useQuery({ queryKey: ['sports'], queryFn: getSports })
  const configurationsQuery = useQuery({
    queryKey: ['sport-configurations'],
    queryFn: getSportConfigurations,
  })

  const tournament = tournamentQuery.data
  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['tournament', tournamentId] }),
      queryClient.invalidateQueries({ queryKey: ['competitions', tournamentId] }),
      queryClient.invalidateQueries({ queryKey: ['tournaments'] }),
    ])
  }

  const reportError = (error: unknown) =>
    setActionError(error instanceof Error ? error.message : 'Action failed')

  const tournamentTransition = useMutation({
    mutationFn: (action: TournamentAction) => transitionTournament(tournamentId, action),
    onSuccess: async () => {
      setActionError('')
      await refresh()
    },
    onError: reportError,
  })

  const approvalPolicy = useMutation({
    mutationFn: (policy: ApprovalPolicy) => setApprovalPolicy(tournamentId, policy),
    onSuccess: async () => {
      setActionError('')
      await queryClient.invalidateQueries({ queryKey: ['tournament', tournamentId] })
    },
    onError: reportError,
  })

  const competitionTransition = useMutation({
    mutationFn: ({ id, action }: { id: string; action: CompetitionAction }) =>
      transitionCompetition(id, action),
    onSuccess: async () => {
      setActionError('')
      await refresh()
    },
    onError: reportError,
  })

  /**
   * Adding a competition needs a SportConfiguration. Rather than making the organizer author one,
   * reuse the tenant's existing configuration for that sport, or create the canonical preset the
   * first time the sport is used here.
   */
  const addCompetition = useMutation({
    mutationFn: async () => {
      if (!tournament) {
        throw new Error('Tournament not loaded yet.')
      }
      const sport = sportsQuery.data?.find((candidate) => candidate.code === sportCode)
      if (!sport) {
        throw new Error('Pick a sport first.')
      }

      let configuration = configurationsQuery.data?.find(
        (candidate) =>
          candidate.sportId === sport.id &&
          candidate.organizationUnitId === tournament.organizationUnitId,
      )

      if (!configuration) {
        const config = buildConfigFor(sport.code)
        if (!config) {
          throw new Error(`No default configuration exists for ${sport.name} yet.`)
        }
        configuration = await createSportConfiguration({
          organizationUnitId: tournament.organizationUnitId,
          sportId: sport.id,
          config,
        })
      }

      return createCompetition(tournamentId, {
        name: competitionName,
        sportConfigurationId: configuration.id,
        maxRegistrations: maxRegistrations ? Number(maxRegistrations) : undefined,
      })
    },
    onSuccess: async () => {
      setActionError('')
      setCompetitionName('')
      setMaxRegistrations('')
      await queryClient.invalidateQueries({ queryKey: ['sport-configurations'] })
      await refresh()
    },
    onError: reportError,
  })

  if (tournamentQuery.isLoading) {
    return (
      <>
        <Header />
        <main className="flex min-h-screen items-center justify-center bg-dark-bg">
          <p className="text-gray-400">Loading tournament…</p>
        </main>
      </>
    )
  }

  if (tournamentQuery.isError || !tournament) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-dark-bg">
          <div className="mx-auto max-w-4xl px-6 py-20 text-center">
            <div className="mb-4 text-5xl">🏟️</div>
            <h1 className="mb-2 text-2xl font-bold text-white">Tournament not found</h1>
            <p className="mb-6 text-gray-400">
              It may have been deleted, or it belongs to an organization you cannot access.
            </p>
            <Link href="/dashboard">
              <Button className="btn-gradient inline-flex">Back to dashboard</Button>
            </Link>
          </div>
        </main>
      </>
    )
  }

  const advance = nextAction(tournament.status)
  const isCancellable =
    can('tournament:transition') && !['COMPLETED', 'CANCELLED', 'ARCHIVED'].includes(tournament.status)
  const competitions = competitionsQuery.data ?? []
  const canAddCompetitions =
    can('competition:create') && !['COMPLETED', 'CANCELLED', 'ARCHIVED'].includes(tournament.status)
  const canConfigureApproval = can('tournament:update')

  const sportOptions = (sportsQuery.data ?? [])
    .filter((sport) => hasPresetFor(sport.code))
    .map((sport) => ({ value: sport.code, label: sport.name }))

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-8 sm:py-12">
          <div className="mx-auto max-w-5xl px-6 sm:px-8">
            <Link
              href="/dashboard"
              className="mb-6 inline-flex items-center gap-1 text-sm text-accent-cyan transition hover:text-accent-cyan/80"
            >
              ← Back to Dashboard
            </Link>

            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-white sm:text-4xl">{tournament.name}</h1>
                <p className="mt-2 text-gray-400">
                  /t/{tournament.slug} · {formatDateRange(tournament.startDate, tournament.endDate)}
                </p>
              </div>
              <StatusBadge status={tournament.status} className="self-start" />
            </div>

            {tournament.description ? (
              <p className="mt-4 max-w-2xl text-gray-400">{tournament.description}</p>
            ) : null}

            <div className="mt-6 flex flex-wrap gap-3">
              {advance && can('tournament:transition') ? (
                <Button
                  className="btn-gradient"
                  disabled={tournamentTransition.isPending}
                  onClick={() => tournamentTransition.mutate(advance.action)}
                >
                  {tournamentTransition.isPending ? 'Working…' : advance.label}
                </Button>
              ) : null}
              {tournament.status !== 'DRAFT' ? (
                <a href={`/t?slug=${tournament.slug}`} target="_blank" rel="noreferrer">
                  <Button variant="secondary">View public page</Button>
                </a>
              ) : null}
              {isCancellable ? (
                <Button
                  variant="ghost"
                  className="text-red-300 hover:text-red-200"
                  disabled={tournamentTransition.isPending}
                  onClick={() => tournamentTransition.mutate('cancel')}
                >
                  Cancel tournament
                </Button>
              ) : null}
            </div>

            {actionError ? (
              <div className="mt-4 rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-3 text-sm text-red-300">
                {actionError}
              </div>
            ) : null}
          </div>
        </div>

        <div className="mx-auto max-w-5xl px-6 py-8 sm:px-8 sm:py-12">
          {/* Entry approval — applies to every competition in this tournament. */}
          {canConfigureApproval ? (
          <Card className="mb-8">
            <h2 className="text-lg font-semibold text-white">Entry approval</h2>
            <p className="mt-1 text-sm text-gray-500">
              Applies to every competition in this tournament.
            </p>

            <div className="mt-5 grid gap-3 sm:grid-cols-2">
              {APPROVAL_CHOICES.map((choice) => {
                const isSelected = tournament.effectiveApprovalPolicy === choice.value
                return (
                  <button
                    key={choice.value}
                    type="button"
                    disabled={approvalPolicy.isPending}
                    onClick={() => approvalPolicy.mutate(choice.value)}
                    aria-pressed={isSelected}
                    className={`rounded-lg border px-4 py-3 text-left transition disabled:opacity-60 ${
                      isSelected
                        ? 'border-accent-purple bg-accent-purple/15'
                        : 'border-dark-border bg-dark-bg/40 hover:border-gray-600'
                    }`}
                  >
                    <span className="block text-sm font-semibold text-white">{choice.label}</span>
                    <span className="mt-1 block text-xs leading-relaxed text-gray-500">
                      {choice.description}
                    </span>
                  </button>
                )
              })}
            </div>

            <div className="mt-4 flex items-start gap-2.5 rounded-lg border border-accent-blue/35 bg-accent-blue/10 px-4 py-3 text-sm text-blue-200">
              <svg className="mt-0.5 h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path
                  fillRule="evenodd"
                  d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm.75-11a.75.75 0 0 0-1.5 0v.5a.75.75 0 0 0 1.5 0V7Zm0 3.25a.75.75 0 0 0-1.5 0v3a.75.75 0 0 0 1.5 0v-3Z"
                  clipRule="evenodd"
                />
              </svg>
              <span>Changing this never touches entries that already exist — those keep whatever
                status they have now.</span>
            </div>

            {tournament.approvalPolicy === null ? (
              <p className="mt-3 text-xs text-gray-600">
                Following the organization default. Choosing an option above sets it for this
                tournament only.
              </p>
            ) : null}
          </Card>
          ) : null}

          <h2 className="mb-6 text-xl font-semibold text-white">
            Competitions
            <span className="ml-2 text-sm font-normal text-gray-500">({competitions.length})</span>
          </h2>

          {competitionsQuery.isLoading ? (
            <p className="text-gray-400">Loading competitions…</p>
          ) : competitions.length === 0 ? (
            <Card className="mb-8 text-center">
              <p className="text-gray-400">
                No competitions yet. A tournament needs at least one before registration can open.
              </p>
            </Card>
          ) : (
            <div className="mb-8 grid gap-4">
              {competitions.map((competition) => {
                const step = nextCompetitionAction(competition.status)
                const isPending =
                  competitionTransition.isPending && competitionTransition.variables?.id === competition.id

                return (
                  <Card key={competition.id}>
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <Link
                          href={`/dashboard/competition?id=${competition.id}&tournamentId=${tournamentId}`}
                        >
                          <h3 className="text-lg font-semibold text-white transition hover:text-accent-purple">
                            {competition.name}
                          </h3>
                        </Link>
                        <p className="mt-1 text-sm text-gray-500">
                          {competition.sportCode}
                          {competition.participantType ? ` · ${competition.participantType}` : ''}
                          {competition.maxRegistrations
                            ? ` · max ${competition.maxRegistrations}`
                            : ''}
                        </p>
                      </div>
                      <div className="flex items-center gap-3">
                        <StatusBadge status={competition.status} />
                        {step ? (
                          <Button
                            variant="secondary"
                            className="px-4 py-2 text-sm"
                            disabled={isPending}
                            onClick={() =>
                              competitionTransition.mutate({ id: competition.id, action: step.action })
                            }
                          >
                            {isPending ? '…' : step.label}
                          </Button>
                        ) : null}
                      </div>
                    </div>
                  </Card>
                )
              })}
            </div>
          )}

          {canAddCompetitions ? (
            <Card>
              <h3 className="mb-4 text-base font-semibold text-white">Add a competition</h3>
              <form
                className="grid gap-4 sm:grid-cols-2"
                onSubmit={(event) => {
                  event.preventDefault()
                  addCompetition.mutate()
                }}
              >
                <div className="sm:col-span-2">
                  <label htmlFor="competitionName" className="mb-2 block text-sm font-medium text-gray-300">
                    Name *
                  </label>
                  <Input
                    id="competitionName"
                    placeholder="e.g., Football U16 Boys"
                    value={competitionName}
                    onChange={(event) => setCompetitionName(event.target.value)}
                    required
                  />
                </div>

                <div>
                  <label htmlFor="sportCode" className="mb-2 block text-sm font-medium text-gray-300">
                    Sport *
                  </label>
                  <Select
                    id="sportCode"
                    value={sportCode}
                    onChange={setSportCode}
                    options={sportOptions}
                    placeholder={sportsQuery.isLoading ? 'Loading sports…' : 'Select a sport'}
                  />
                </div>

                <div>
                  <label htmlFor="maxRegistrations" className="mb-2 block text-sm font-medium text-gray-300">
                    Max entries
                  </label>
                  <Input
                    id="maxRegistrations"
                    type="number"
                    min={1}
                    placeholder="Leave empty for no limit"
                    value={maxRegistrations}
                    onChange={(event) => setMaxRegistrations(event.target.value)}
                  />
                </div>

                <div className="sm:col-span-2">
                  <Button
                    type="submit"
                    className="btn-gradient"
                    disabled={addCompetition.isPending || !competitionName || !sportCode}
                  >
                    {addCompetition.isPending ? 'Adding…' : 'Add competition'}
                  </Button>
                  <p className="mt-3 text-xs text-gray-500">
                    The scoring and fixture rules for the sport are applied automatically.
                  </p>
                </div>
              </form>
            </Card>
          ) : null}
        </div>
      </main>
    </>
  )
}
