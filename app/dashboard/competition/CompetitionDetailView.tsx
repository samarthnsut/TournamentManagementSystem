'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import Header from '../../../components/Header'
import Button from '../../../components/ui/Button'
import Card from '../../../components/ui/Card'
import Input from '../../../components/ui/Input'
import StatusBadge from '../../../components/ui/StatusBadge'
import DynamicForm, { validateAnswers } from '../../../components/registration/DynamicForm'
import FixturesPanel from '../../../components/competition/FixturesPanel'
import LeaderboardPanel from '../../../components/competition/LeaderboardPanel'
import FormBuilder, {
  buildSchema,
  emptyField,
  fieldsFromSchema,
  type BuilderField,
} from '../../../components/registration/FormBuilder'
import { useAuth } from '../../../lib/useAuth'
import { approveRegistration, rejectRegistration } from '../../../lib/api/approvals'
import {
  getActiveFormDefinition,
  getFormDefinitions,
  getRegistrations,
  publishFormDefinition,
  submitRegistration,
  withdrawRegistration,
  type FormSchema,
  type TeamMember,
} from '../../../lib/api/registrations'
import {
  getCompetitions,
  getTournament,
  nextCompetitionAction,
  transitionCompetition,
  type Competition,
} from '../../../lib/api/tournaments'

export default function CompetitionDetailView({
  competitionId,
  tournamentId,
}: {
  competitionId: string
  tournamentId: string
}) {
  const queryClient = useQueryClient()
  const { can } = useAuth()
  const [error, setError] = useState('')
  const [builderFields, setBuilderFields] = useState<BuilderField[]>([emptyField()])
  const [isEditingForm, setIsEditingForm] = useState(false)

  const [entrantName, setEntrantName] = useState('')
  const [members, setMembers] = useState<TeamMember[]>([])
  const [answers, setAnswers] = useState<Record<string, unknown>>({})
  const [answerErrors, setAnswerErrors] = useState<Record<string, string>>({})

  const tournamentQuery = useQuery({
    queryKey: ['tournament', tournamentId],
    queryFn: () => getTournament(tournamentId),
    enabled: Boolean(tournamentId),
  })

  // The API has no single-competition read that also gives us the tournament context, so the
  // competition is picked out of its tournament's list.
  const competitionsQuery = useQuery({
    queryKey: ['competitions', tournamentId],
    queryFn: () => getCompetitions(tournamentId),
    enabled: Boolean(tournamentId),
  })
  const competition: Competition | undefined = competitionsQuery.data?.find(
    (candidate) => candidate.id === competitionId,
  )

  const versionsQuery = useQuery({
    queryKey: ['form-definitions', competitionId],
    queryFn: () => getFormDefinitions(competitionId),
    enabled: Boolean(competitionId),
  })

  const activeFormQuery = useQuery({
    queryKey: ['active-form', competitionId],
    queryFn: () => getActiveFormDefinition(competitionId),
    enabled: Boolean(competitionId),
    // A competition without a published form answers 409; that is a state, not a failure.
    retry: false,
  })

  const registrationsQuery = useQuery({
    queryKey: ['registrations', competitionId],
    queryFn: () => getRegistrations(competitionId),
    enabled: Boolean(competitionId),
  })

  const activeForm = activeFormQuery.data
  const hasActiveForm = Boolean(activeForm)

  // Seed the builder from the published form the first time it arrives.
  useEffect(() => {
    if (activeForm && !isEditingForm) {
      const existing = fieldsFromSchema(activeForm.schema)
      setBuilderFields(existing.length > 0 ? existing : [emptyField()])
    }
  }, [activeForm, isEditingForm])

  const report = (cause: unknown) =>
    setError(cause instanceof Error ? cause.message : 'Something went wrong')

  const publishForm = useMutation({
    mutationFn: (schema: FormSchema) => publishFormDefinition(competitionId, schema),
    onSuccess: async () => {
      setError('')
      setIsEditingForm(false)
      await queryClient.invalidateQueries({ queryKey: ['active-form', competitionId] })
      await queryClient.invalidateQueries({ queryKey: ['form-definitions', competitionId] })
    },
    onError: report,
  })

  const transition = useMutation({
    mutationFn: (action: Parameters<typeof transitionCompetition>[1]) =>
      transitionCompetition(competitionId, action),
    onSuccess: async () => {
      setError('')
      await queryClient.invalidateQueries({ queryKey: ['competitions', tournamentId] })
    },
    onError: report,
  })

  const register = useMutation({
    mutationFn: () =>
      submitRegistration({
        competitionId,
        participant: {
          participantType: competition?.participantType ?? 'INDIVIDUAL',
          displayName: entrantName,
          ...(competition?.participantType === 'TEAM' ? { members } : {}),
        },
        answers,
      }),
    onSuccess: async () => {
      setError('')
      setEntrantName('')
      setMembers([])
      setAnswers({})
      setAnswerErrors({})
      await queryClient.invalidateQueries({ queryKey: ['registrations', competitionId] })
    },
    onError: report,
  })

  const approve = useMutation({
    mutationFn: (registrationId: string) => approveRegistration(registrationId),
    onSuccess: async () => {
      setError('')
      await queryClient.invalidateQueries({ queryKey: ['registrations', competitionId] })
    },
    onError: report,
  })

  const reject = useMutation({
    mutationFn: (registrationId: string) => {
      const reason = window.prompt('Why is this entry being rejected? The entrant is told.')
      if (!reason || !reason.trim()) {
        return Promise.reject(new Error('A reason is required when rejecting.'))
      }
      return rejectRegistration(registrationId, reason.trim())
    },
    onSuccess: async () => {
      setError('')
      await queryClient.invalidateQueries({ queryKey: ['registrations', competitionId] })
    },
    onError: report,
  })

  const withdraw = useMutation({
    mutationFn: withdrawRegistration,
    onSuccess: async () => {
      setError('')
      await queryClient.invalidateQueries({ queryKey: ['registrations', competitionId] })
    },
    onError: report,
  })

  if (competitionsQuery.isLoading) {
    return (
      <>
        <Header />
        <main className="flex min-h-screen items-center justify-center bg-dark-bg">
          <p className="text-gray-400">Loading competition…</p>
        </main>
      </>
    )
  }

  if (!competition) {
    return (
      <>
        <Header />
        <main className="min-h-screen bg-dark-bg">
          <div className="mx-auto max-w-4xl px-6 py-20 text-center">
            <div className="mb-4 text-5xl">🎽</div>
            <h1 className="mb-2 text-2xl font-bold text-white">Competition not found</h1>
            <Link href="/dashboard">
              <Button className="btn-gradient mt-4 inline-flex">Back to dashboard</Button>
            </Link>
          </div>
        </main>
      </>
    )
  }

  const step = nextCompetitionAction(competition.status)
  const isTeamEvent = competition.participantType === 'TEAM'
  const canEnter = competition.status === 'OPEN' && hasActiveForm && can('registration:create')

  const allRegistrations = registrationsQuery.data ?? []
  const liveRegistrations = allRegistrations.filter((r) => r.status !== 'WITHDRAWN')
  const pendingCount = allRegistrations.filter((r) => r.status === 'PENDING').length
  const approvedCount = allRegistrations.filter((r) => r.status === 'APPROVED').length
  const submittedCount = allRegistrations.length

  // No published form means the builder is the only thing to show, so it is a draft either way.
  const canEditForm = can('form:create')
  const canDecide = can('registration:approve')
  // Without form permissions there is nothing to draft, only the published form to read.
  const isDraftingForm = canEditForm && (!hasActiveForm || isEditingForm)
  const versionCount = versionsQuery.data?.length ?? 0
  const nextVersion = (activeForm?.version ?? 0) + 1

  const autoApproves = tournamentQuery.data?.effectiveApprovalPolicy === 'AUTO_APPROVE'

  const handleRegister = (event: React.FormEvent) => {
    event.preventDefault()
    if (!activeForm) {
      return
    }
    const found = validateAnswers(activeForm.schema, answers)
    setAnswerErrors(found)
    if (Object.keys(found).length === 0) {
      register.mutate()
    }
  }

  return (
    <>
      <Header />
      <main className="min-h-screen bg-dark-bg">
        <div className="border-b border-dark-border bg-gradient-to-b from-dark-surface to-dark-bg py-8 sm:py-12">
          <div className="mx-auto max-w-5xl px-6 sm:px-8">
            <Link
              href={`/dashboard/tournament?id=${tournamentId}`}
              className="mb-6 inline-flex items-center gap-1 text-sm text-accent-cyan transition hover:text-accent-cyan/80"
            >
              ← {tournamentQuery.data?.name ?? 'Back to tournament'}
            </Link>

            <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
              <div>
                <h1 className="text-3xl font-bold text-white sm:text-4xl">{competition.name}</h1>
                <p className="mt-2 text-gray-400">
                  {competition.sportCode} · {competition.participantType}
                  {competition.maxRegistrations ? ` · max ${competition.maxRegistrations} entries` : ''}
                </p>
              </div>
              <StatusBadge status={competition.status} className="self-start" />
            </div>

            <div className="mt-6 flex flex-wrap gap-3">
              {step && can('competition:transition') ? (
                <Button
                  className="btn-gradient"
                  disabled={transition.isPending}
                  onClick={() => transition.mutate(step.action)}
                >
                  {transition.isPending ? 'Working…' : step.label}
                </Button>
              ) : null}
            </div>

            {error ? (
              <div className="mt-4 rounded-lg border border-red-500/30 bg-red-500/5 px-4 py-3 text-sm text-red-300">
                {error}
              </div>
            ) : null}
          </div>
        </div>

        <div className="mx-auto max-w-5xl space-y-8 px-6 py-8 sm:px-8 sm:py-12">
          {/* Registration form definition */}
          <Card className={isDraftingForm ? 'border-l-4 border-l-amber-500' : ''}>
            <div className="mb-5 flex flex-wrap items-start justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-white">
                  {isDraftingForm ? 'Editing the registration form' : 'Registration form'}
                </h2>
                <p className="mt-1 text-sm text-gray-500">
                  {isDraftingForm
                    ? hasActiveForm
                      ? `Draft of version ${nextVersion} · not visible to entrants yet`
                      : 'Not published yet · entrants cannot enter until it is published'
                    : `Version ${activeForm?.version} is live${
                        versionCount > 1 ? ` · ${versionCount} versions published` : ''
                      }`}
                </p>
              </div>
              {isDraftingForm ? (
                <span className="whitespace-nowrap rounded-full border border-amber-500/40 bg-amber-500/20 px-3 py-1 text-xs font-semibold text-amber-300">
                  Unpublished draft
                </span>
              ) : !canEditForm ? null : (
                <Button
                  variant="secondary"
                  className="px-4 py-2 text-sm"
                  onClick={() => setIsEditingForm(true)}
                >
                  Edit form
                </Button>
              )}
            </div>

            {isDraftingForm ? (
              <>
                {/* The consequence, stated where the decision is made rather than above it. */}
                <div className="mb-5 flex items-start gap-2.5 rounded-lg border border-amber-500/35 bg-amber-500/10 px-4 py-3 text-sm text-amber-200">
                  <svg className="mt-0.5 h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path
                      fillRule="evenodd"
                      d="M8.49 3.17a1.75 1.75 0 0 1 3.02 0l6.28 10.8A1.75 1.75 0 0 1 16.28 16.6H3.72a1.75 1.75 0 0 1-1.51-2.63l6.28-10.8ZM10 7a.75.75 0 0 1 .75.75v3a.75.75 0 0 1-1.5 0v-3A.75.75 0 0 1 10 7Zm0 7a1 1 0 1 0 0-2 1 1 0 0 0 0 2Z"
                      clipRule="evenodd"
                    />
                  </svg>
                  <span>
                    {hasActiveForm ? (
                      <>
                        Publishing creates <strong className="font-semibold">version {nextVersion}</strong>.
                        {submittedCount > 0
                          ? ` The ${submittedCount} ${
                              submittedCount === 1 ? 'entry' : 'entries'
                            } already submitted keep version ${activeForm?.version} and stay valid.`
                          : ' No entries have been submitted yet.'}
                      </>
                    ) : (
                      <>Publishing makes this form live so entrants can start submitting.</>
                    )}
                  </span>
                </div>

                <div className="grid gap-6 lg:grid-cols-[1.35fr_1fr]">
                  <FormBuilder
                    fields={builderFields}
                    onChange={setBuilderFields}
                    disabled={publishForm.isPending}
                  />

                  {/* Kept alongside the builder — this is exactly when you want to see it. */}
                  <div>
                    <p className="mb-3 font-mono text-xs uppercase tracking-wider text-gray-500">
                      What entrants see
                    </p>
                    <div className="rounded-lg border border-dark-border bg-dark-bg/40 p-4">
                      <DynamicForm
                        schema={buildSchema(builderFields)}
                        values={{}}
                        onChange={() => {}}
                        readOnly
                      />
                    </div>
                  </div>
                </div>

                <div className="mt-6 flex flex-wrap gap-3 border-t border-dark-border pt-5">
                  <Button
                    className="btn-gradient"
                    disabled={publishForm.isPending}
                    onClick={() => publishForm.mutate(buildSchema(builderFields))}
                  >
                    {publishForm.isPending
                      ? 'Publishing…'
                      : hasActiveForm
                        ? `Publish version ${nextVersion}`
                        : 'Publish form'}
                  </Button>
                  {hasActiveForm ? (
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setIsEditingForm(false)
                        setBuilderFields(fieldsFromSchema(activeForm!.schema))
                      }}
                    >
                      Discard changes
                    </Button>
                  ) : null}
                </div>
              </>
            ) : activeForm ? (
              <div className="rounded-lg border border-dark-border bg-dark-bg/40 p-5">
                <p className="mb-4 font-mono text-xs uppercase tracking-wider text-gray-500">
                  What entrants see
                </p>
                <DynamicForm schema={activeForm.schema} values={{}} onChange={() => {}} readOnly />
              </div>
            ) : (
              // No form published, and this viewer cannot publish one.
              <p className="text-sm text-gray-500">
                No registration form has been published for this competition yet.
              </p>
            )}
          </Card>

          {/* Enter a participant */}
          {canEnter ? (
            <Card>
              <h2 className="mb-1 text-lg font-semibold text-white">Add an entry</h2>
              <p className="mb-5 text-sm text-gray-500">
                Register {isTeamEvent ? 'a team' : 'an athlete'} on their behalf. Answers are
                validated against version {activeForm?.version}.
              </p>

              <form onSubmit={handleRegister} className="space-y-5">
                <div>
                  <label htmlFor="entrantName" className="mb-2 block text-sm font-medium text-gray-300">
                    {isTeamEvent ? 'Team name' : 'Athlete name'}
                    <span className="ml-1 text-accent-pink">*</span>
                  </label>
                  <Input
                    id="entrantName"
                    value={entrantName}
                    onChange={(event) => setEntrantName(event.target.value)}
                    placeholder={isTeamEvent ? 'e.g., Sonipat Strikers' : 'e.g., Ravi Kumar'}
                    required
                  />
                </div>

                {isTeamEvent ? (
                  <div>
                    <label className="mb-2 block text-sm font-medium text-gray-300">Squad</label>
                    <div className="space-y-3">
                      {members.map((member, index) => (
                        <div key={index} className="flex flex-wrap items-center gap-3">
                          <Input
                            className="flex-1"
                            placeholder="Full name"
                            value={member.fullName}
                            onChange={(event) =>
                              setMembers(
                                members.map((current, position) =>
                                  position === index
                                    ? { ...current, fullName: event.target.value }
                                    : current,
                                ),
                              )
                            }
                          />
                          <label className="flex items-center gap-2 text-sm text-gray-400">
                            <input
                              type="radio"
                              name="captain"
                              checked={member.memberRole === 'CAPTAIN'}
                              onChange={() =>
                                setMembers(
                                  members.map((current, position) => ({
                                    ...current,
                                    memberRole: position === index ? 'CAPTAIN' : 'PLAYER',
                                  })),
                                )
                              }
                              className="accent-accent-purple"
                            />
                            Captain
                          </label>
                          <button
                            type="button"
                            onClick={() => setMembers(members.filter((_, p) => p !== index))}
                            className="text-sm text-red-300 transition hover:text-red-200"
                          >
                            Remove
                          </button>
                        </div>
                      ))}
                    </div>
                    <Button
                      type="button"
                      variant="secondary"
                      className="mt-3 px-4 py-2 text-sm"
                      onClick={() =>
                        setMembers([...members, { fullName: '', memberRole: members.length === 0 ? 'CAPTAIN' : 'PLAYER' }])
                      }
                    >
                      + Add squad member
                    </Button>
                  </div>
                ) : null}

                {activeForm ? (
                  <div className="border-t border-dark-border pt-5">
                    <DynamicForm
                      schema={activeForm.schema}
                      values={answers}
                      onChange={setAnswers}
                      errors={answerErrors}
                    />
                  </div>
                ) : null}

                <Button type="submit" className="btn-gradient" disabled={register.isPending || !entrantName}>
                  {register.isPending ? 'Submitting…' : 'Submit entry'}
                </Button>
              </form>
            </Card>
          ) : (
            <Card>
              <h2 className="mb-1 text-lg font-semibold text-white">Add an entry</h2>
              <p className="text-sm text-gray-500">
                {!hasActiveForm
                  ? 'Publish a registration form first.'
                  : `Entries are only accepted while the competition is open. It is currently ${competition.status.toLowerCase()}.`}
              </p>
            </Card>
          )}

          {/* Entries */}
          <Card>
            <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-lg font-semibold text-white">Entries</h2>
              {/* The two questions an organizer actually has: how many need me, and how full is it. */}
              <div className="flex flex-wrap items-center gap-2">
                {pendingCount > 0 ? (
                  <span className="whitespace-nowrap rounded-full border border-amber-500/40 bg-amber-500/20 px-3 py-1 text-xs font-semibold text-amber-300">
                    {pendingCount} awaiting approval
                  </span>
                ) : null}
                {approvedCount > 0 ? (
                  <span className="whitespace-nowrap rounded-full border border-green-500/40 bg-green-500/20 px-3 py-1 text-xs font-semibold text-green-300">
                    {approvedCount} accepted
                  </span>
                ) : null}
                <span className="whitespace-nowrap rounded-full border border-dark-border bg-white/5 px-3 py-1 text-xs font-semibold text-gray-400">
                  {liveRegistrations.length}
                  {competition.maxRegistrations ? ` of ${competition.maxRegistrations}` : ''} places
                </span>
              </div>
            </div>

            <div className="mb-5 flex items-start gap-2.5 rounded-lg border border-accent-blue/35 bg-accent-blue/10 px-4 py-3 text-sm text-blue-200">
              <svg className="mt-0.5 h-4 w-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path
                  fillRule="evenodd"
                  d="M10 18a8 8 0 1 0 0-16 8 8 0 0 0 0 16Zm.75-11a.75.75 0 0 0-1.5 0v.5a.75.75 0 0 0 1.5 0V7Zm0 3.25a.75.75 0 0 0-1.5 0v3a.75.75 0 0 0 1.5 0v-3Z"
                  clipRule="evenodd"
                />
              </svg>
              <span>
                {autoApproves
                  ? 'This tournament accepts entries automatically. Change it in tournament settings.'
                  : 'Entries need approval before they count. Change it in tournament settings.'}
              </span>
            </div>

            {registrationsQuery.isLoading ? (
              <p className="text-sm text-gray-400">Loading entries…</p>
            ) : submittedCount === 0 ? (
              <p className="text-sm text-gray-500">No entries yet.</p>
            ) : (
              <div className="space-y-3">
                {allRegistrations.map((registration) => (
                  <div
                    key={registration.id}
                    className="rounded-lg border border-dark-border bg-dark-bg/40 p-4"
                  >
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <p className="font-semibold text-white">{registration.participant.displayName}</p>
                      <StatusBadge status={registration.status} />
                    </div>

                    {/* Separated so each fact is scannable rather than one grey run-on line. */}
                    <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1 text-xs text-gray-500">
                      {registration.participant.members.length > 0 ? (
                        <span>{registration.participant.members.length} squad members</span>
                      ) : null}
                      <span>Answered form v{registration.formVersion}</span>
                      <span>
                        {new Date(registration.submittedAt).toLocaleString('en-IN', {
                          day: 'numeric',
                          month: 'short',
                          hour: 'numeric',
                          minute: '2-digit',
                        })}
                      </span>
                    </div>

                    {registration.status === 'PENDING' ? (
                      <div className="mt-3 flex flex-wrap items-center gap-4 border-t border-dark-border pt-3">
                        {canDecide ? (
                        <>
                        <button
                          type="button"
                          onClick={() => approve.mutate(registration.id)}
                          disabled={approve.isPending}
                          className="rounded-lg border border-green-500/40 bg-green-500/10 px-4 py-2 text-sm font-semibold text-green-300 transition hover:border-green-400 hover:bg-green-500/20 hover:text-green-200 focus:outline-none focus:ring-2 focus:ring-green-500/40 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {approve.isPending ? 'Approving…' : 'Approve'}
                        </button>
                        <button
                          type="button"
                          onClick={() => reject.mutate(registration.id)}
                          disabled={reject.isPending}
                          className="rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-2 text-sm font-semibold text-red-300 transition hover:border-red-400 hover:bg-red-500/20 hover:text-red-200 focus:outline-none focus:ring-2 focus:ring-red-500/40 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {reject.isPending ? 'Rejecting…' : 'Reject'}
                        </button>
                        </>
                        ) : null}
                        <button
                          type="button"
                          onClick={() => withdraw.mutate(registration.id)}
                          disabled={withdraw.isPending}
                          className="rounded-lg border border-dark-border px-4 py-2 text-sm font-medium text-gray-400 transition hover:border-gray-600 hover:bg-white/5 hover:text-gray-200 focus:outline-none focus:ring-2 focus:ring-gray-500/30 disabled:cursor-not-allowed disabled:opacity-50"
                        >
                          {withdraw.isPending ? 'Withdrawing…' : 'Withdraw'}
                        </button>
                      </div>
                    ) : null}
                  </div>
                ))}
              </div>
            )}
          </Card>

          {/* Fixtures and standings only mean anything once entries are settled, so they sit
              after the entry list — the order an organizer's day actually runs in. */}
          {competition.status !== 'DRAFT' && competition.status !== 'OPEN' ? (
            <>
              <FixturesPanel
                competition={competition}
                canGenerate={can('fixture:generate')}
                canRecord={can('result:record')}
                canSchedule={can('match:schedule')}
                onError={report}
              />
              <LeaderboardPanel competitionId={competitionId} />
            </>
          ) : null}
        </div>
      </main>
    </>
  )
}
