import { request } from './client'

export type TournamentStatus =
  | 'DRAFT'
  | 'PUBLISHED'
  | 'REGISTRATION_OPEN'
  | 'REGISTRATION_CLOSED'
  | 'IN_PROGRESS'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'ARCHIVED'

export type CompetitionStatus = 'DRAFT' | 'OPEN' | 'CLOSED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'

/** 07_APPROVAL_WORKFLOW_ENGINE section 7.2. INHERIT clears a tournament's override. */
export type ApprovalPolicy = 'AUTO_APPROVE' | 'DIRECT_SINGLE_APPROVAL'

export type Tournament = {
  id: string
  organizationUnitId: string
  name: string
  slug: string
  description: string | null
  status: TournamentStatus
  startDate: string | null
  endDate: string | null
  publishedAt: string | null
  /** The tournament's own choice, or null when it follows the organization. */
  approvalPolicy: ApprovalPolicy | null
  /** What applies once inheritance is resolved — show this, not the override. */
  effectiveApprovalPolicy: ApprovalPolicy
  createdAt: string
}

export type Competition = {
  id: string
  tournamentId: string
  organizationUnitId: string
  name: string
  sportId: string
  sportCode: string | null
  sportConfigurationId: string
  participantType: 'INDIVIDUAL' | 'TEAM' | 'ORGANIZATION' | null
  /**
   * The resolved strategy keys. Dispatch on these — never on `sportCode` — so a sport this UI has
   * never heard of still gets the right controls.
   */
  fixtureGenerator: import('./fixtures').FixtureGeneratorKey | null
  resultEvaluator: import('./fixtures').ResultEvaluatorKey | null
  leaderboardStrategy: import('./fixtures').LeaderboardStrategyKey | null
  status: CompetitionStatus
  maxRegistrations: number | null
  registrationOpenAt: string | null
  registrationCloseAt: string | null
}

export type Sport = {
  id: string
  code: string
  name: string
  description: string | null
}

export type SportConfiguration = {
  id: string
  organizationUnitId: string
  sportId: string
  config: Record<string, unknown>
  version: number
  isActive: boolean
  createdAt: string
}

export type CreateTournamentPayload = {
  organizationUnitId: string
  name: string
  slug?: string
  description?: string
  startDate?: string
  endDate?: string
}

export type CreateCompetitionPayload = {
  name: string
  sportConfigurationId: string
  maxRegistrations?: number
}

/** The lifecycle actions exposed by the backend, in the order they may be taken. */
export type TournamentAction =
  | 'publish'
  | 'open-registration'
  | 'close-registration'
  | 'start'
  | 'complete'
  | 'cancel'
  | 'archive'

/** The single next step for a tournament, or null when it has run its course. */
export function nextAction(status: TournamentStatus): { action: TournamentAction; label: string } | null {
  switch (status) {
    case 'DRAFT':
      return { action: 'publish', label: 'Publish' }
    case 'PUBLISHED':
      return { action: 'open-registration', label: 'Open registration' }
    case 'REGISTRATION_OPEN':
      return { action: 'close-registration', label: 'Close registration' }
    case 'REGISTRATION_CLOSED':
      return { action: 'start', label: 'Start' }
    case 'IN_PROGRESS':
      return { action: 'complete', label: 'Complete' }
    case 'COMPLETED':
      return { action: 'archive', label: 'Archive' }
    default:
      return null
  }
}

export type CompetitionAction = 'open' | 'close' | 'start' | 'complete' | 'cancel'

/** The single next step for a competition, or null once it is finished. */
export function nextCompetitionAction(
  status: CompetitionStatus,
): { action: CompetitionAction; label: string } | null {
  switch (status) {
    case 'DRAFT':
      return { action: 'open', label: 'Open' }
    case 'OPEN':
      return { action: 'close', label: 'Close' }
    case 'CLOSED':
      return { action: 'start', label: 'Start' }
    case 'IN_PROGRESS':
      return { action: 'complete', label: 'Complete' }
    default:
      return null
  }
}

export function transitionCompetition(id: string, action: CompetitionAction) {
  return request<{ id: string; status: CompetitionStatus; transitionedAt: string }>(
    `/competitions/${id}/${action}`,
    { method: 'POST', body: JSON.stringify({}) },
  )
}

export function getTournaments() {
  return request<Tournament[]>('/tournaments')
}

export function getTournament(id: string) {
  return request<Tournament>(`/tournaments/${id}`)
}

export function createTournament(payload: CreateTournamentPayload) {
  return request<Tournament>('/tournaments', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function transitionTournament(id: string, action: TournamentAction) {
  return request<{ id: string; status: TournamentStatus; transitionedAt: string }>(
    `/tournaments/${id}/${action}`,
    { method: 'POST', body: JSON.stringify({}) },
  )
}

export function setApprovalPolicy(id: string, approvalPolicy: ApprovalPolicy | 'INHERIT') {
  return request<Tournament>(`/tournaments/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ approvalPolicy }),
  })
}

export function deleteTournament(id: string) {
  return request<void>(`/tournaments/${id}`, { method: 'DELETE' })
}

export function getCompetitions(tournamentId: string) {
  return request<Competition[]>(`/tournaments/${tournamentId}/competitions`)
}

export function createCompetition(tournamentId: string, payload: CreateCompetitionPayload) {
  return request<Competition>(`/tournaments/${tournamentId}/competitions`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function getSports() {
  return request<Sport[]>('/sports')
}

export function getSportConfigurations() {
  return request<SportConfiguration[]>('/sport-configurations')
}

export function createSportConfiguration(payload: {
  organizationUnitId: string
  sportId: string
  config: Record<string, unknown>
}) {
  return request<SportConfiguration>('/sport-configurations', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/** Anonymous: this is the page a visitor sees at /t/{slug}. */
export function getPublicTournament(slug: string) {
  return request<{
    name: string
    slug: string
    status: TournamentStatus
    description: string | null
    startDate: string | null
    endDate: string | null
    organizer: { name: string; type: string } | null
    competitions: Array<{ id: string; name: string; sportCode: string | null; status: CompetitionStatus }>
  }>(`/public/t/${slug}`, { auth: false })
}

/** Replaces the whole config document — the API takes no patch (06 §6: full-document override). */
export function replaceSportConfiguration(id: string, config: Record<string, unknown>) {
  return request<SportConfiguration>(`/sport-configurations/${id}`, {
    method: 'PUT',
    body: JSON.stringify(config),
  })
}
