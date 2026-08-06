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
import FormBuilder, {
  buildSchema,
  emptyField,
  fieldsFromSchema,
  type BuilderField,
} from '../../../components/registration/FormBuilder'
import { ApiError } from '../../../lib/api/client'
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
  const canEnter = competition.status === 'OPEN' && hasActiveForm
  const liveRegistrations = (registrationsQuery.data ?? []).filter(
    (registration) => registration.status !== 'WITHDRAWN',
  )

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
              {step ? (
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
          <Card>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-white">Registration form</h2>
                <p className="mt-1 text-sm text-gray-500">
                  {hasActiveForm
                    ? `Version ${activeForm?.version} is live. Publishing changes creates a new version; existing entries keep the version they answered.`
                    : 'Entries cannot be accepted until a form is published.'}
                </p>
              </div>
              {hasActiveForm && !isEditingForm ? (
                <Button
                  variant="secondary"
                  className="px-4 py-2 text-sm"
                  onClick={() => setIsEditingForm(true)}
                >
                  Edit form
                </Button>
              ) : null}
            </div>

            {!hasActiveForm || isEditingForm ? (
              <>
                <FormBuilder
                  fields={builderFields}
                  onChange={setBuilderFields}
                  disabled={publishForm.isPending}
                />
                <div className="mt-5 flex flex-wrap gap-3">
                  <Button
                    className="btn-gradient"
                    disabled={publishForm.isPending}
                    onClick={() => publishForm.mutate(buildSchema(builderFields))}
                  >
                    {publishForm.isPending
                      ? 'Publishing…'
                      : hasActiveForm
                        ? `Publish version ${(activeForm?.version ?? 0) + 1}`
                        : 'Publish form'}
                  </Button>
                  {isEditingForm ? (
                    <Button
                      variant="ghost"
                      onClick={() => {
                        setIsEditingForm(false)
                        setBuilderFields(
                          activeForm ? fieldsFromSchema(activeForm.schema) : [emptyField()],
                        )
                      }}
                    >
                      Cancel
                    </Button>
                  ) : null}
                </div>
              </>
            ) : (
              <div className="rounded-lg border border-dark-border bg-dark-bg/40 p-5">
                <p className="mb-4 text-xs uppercase tracking-wide text-gray-500">Preview</p>
                <DynamicForm schema={activeForm!.schema} values={{}} onChange={() => {}} readOnly />
              </div>
            )}

            {(versionsQuery.data?.length ?? 0) > 1 ? (
              <p className="mt-4 text-xs text-gray-500">
                {versionsQuery.data?.length} versions published. Earlier entries still render
                against the version they were submitted under.
              </p>
            ) : null}
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
            <h2 className="mb-4 text-lg font-semibold text-white">
              Entries
              <span className="ml-2 text-sm font-normal text-gray-500">
                ({liveRegistrations.length}
                {competition.maxRegistrations ? ` of ${competition.maxRegistrations}` : ''})
              </span>
            </h2>

            {registrationsQuery.isLoading ? (
              <p className="text-sm text-gray-400">Loading entries…</p>
            ) : (registrationsQuery.data?.length ?? 0) === 0 ? (
              <p className="text-sm text-gray-500">No entries yet.</p>
            ) : (
              <div className="space-y-3">
                {registrationsQuery.data?.map((registration) => (
                  <div
                    key={registration.id}
                    className="flex flex-col gap-3 rounded-lg border border-dark-border bg-dark-bg/40 p-4 sm:flex-row sm:items-center sm:justify-between"
                  >
                    <div>
                      <p className="font-medium text-white">{registration.participant.displayName}</p>
                      <p className="mt-1 text-xs text-gray-500">
                        Submitted {new Date(registration.submittedAt).toLocaleString('en-IN')} · form v
                        {registration.formVersion}
                        {registration.participant.members.length > 0
                          ? ` · ${registration.participant.members.length} squad members`
                          : ''}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <StatusBadge status={registration.status} />
                      {registration.status === 'PENDING' ? (
                        <button
                          type="button"
                          onClick={() => withdraw.mutate(registration.id)}
                          disabled={withdraw.isPending}
                          className="text-sm text-gray-400 transition hover:text-red-300 disabled:opacity-50"
                        >
                          Withdraw
                        </button>
                      ) : null}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </Card>
        </div>
      </main>
    </>
  )
}
