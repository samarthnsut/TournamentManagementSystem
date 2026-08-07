import { request } from './client'

export type FixtureGeneratorKey =
  | 'ROUND_ROBIN'
  | 'SINGLE_ELIMINATION'
  | 'DOUBLE_ELIMINATION'
  | 'SWISS'
  | 'NONE'

export type ResultEvaluatorKey = 'POINTS' | 'WIN_LOSS' | 'TIME' | 'DISTANCE' | 'SCORE'

export type LeaderboardStrategyKey =
  | 'POINTS_TABLE'
  | 'LOWEST_TIME'
  | 'HIGHEST_DISTANCE'
  | 'HIGHEST_SCORE'
  | 'BRACKET'

export type MatchStatus =
  | 'SCHEDULED'
  | 'LIVE'
  | 'COMPLETED'
  | 'WALKOVER'
  | 'CANCELLED'
  | 'POSTPONED'

export type ResultOutcome = 'COMPLETED' | 'WALKOVER'

/**
 * `RANKED` is placement by measurement (a race); `NO_CONTEST` is an entrant who produced no result
 * — did not start, did not finish, disqualified — kept on the sheet rather than dropped.
 */
export type Standing = 'WIN' | 'DRAW' | 'LOSS' | 'RANKED' | 'NO_CONTEST'

export type MatchParticipant = {
  participantId: string
  name: string | null
  /** HOME/AWAY for a pairing, LANE_n for a race. A label, never something to branch on. */
  slot: string | null
  seed: number | null
}

export type ParticipantOutcome = {
  participantId: string
  name: string | null
  value: number | null
  unit: string | null
  points: number
  standing: Standing
}

export type ResultSummary = {
  resultId: string
  evaluatorKey: ResultEvaluatorKey
  outcome: ResultOutcome
  winnerParticipantId: string | null
  recordedAt: string
  participants: ParticipantOutcome[]
}

export type Match = {
  id: string
  competitionId: string
  fixtureId: string | null
  round: number | null
  status: MatchStatus
  scheduledAt: string | null
  venueId: string | null
  /** Echoed back when recording a result; a mismatch means someone else got there first. */
  version: number
  participants: MatchParticipant[]
  result: ResultSummary | null
}

export type FixtureRound = {
  fixtureId: string
  round: number
  roundName: string | null
  generatedAt: string
  matches: Match[]
}

export type FixtureSet = {
  competitionId: string
  generatorKey: FixtureGeneratorKey
  rounds: number
  matchCount: number
  fixtures: FixtureRound[]
}

export type LeaderboardEntry = {
  rank: number
  participantId: string
  name: string | null
  /**
   * Strategy-shaped and deliberately untyped (ADR-016): a points table and a timed board share no
   * keys, and the client renders what it is given rather than assuming a sport.
   */
  metrics: Record<string, unknown>
}

export type Leaderboard = {
  competitionId: string
  strategyKey: LeaderboardStrategyKey
  computedAt: string
  /** True once the competition is COMPLETED — the board no longer changes (BR-LE-3). */
  frozen: boolean
  entries: LeaderboardEntry[]
}

export type SeedStrategy = 'RANDOM' | 'SEEDED'

export type ScorePayload = {
  participantId: string
  value: number | null
  unit?: string
}

/** 409 COMPETITION_NOT_CLOSED / FIXTURE_ALREADY_EXISTS / INSUFFICIENT_PARTICIPANTS. */
export function generateFixtures(competitionId: string, seedStrategy: SeedStrategy = 'RANDOM') {
  return request<FixtureSet>(`/competitions/${competitionId}/fixtures/generate`, {
    method: 'POST',
    body: JSON.stringify({ seedStrategy }),
  })
}

/** 409 MATCHES_HAVE_RESULTS once anything has been played (BR-F-3). */
export function regenerateFixtures(competitionId: string, seedStrategy: SeedStrategy = 'RANDOM') {
  return request<FixtureSet>(`/competitions/${competitionId}/fixtures/regenerate`, {
    method: 'POST',
    body: JSON.stringify({ confirm: true, seedStrategy }),
  })
}

/** 404 FIXTURE_NOT_FOUND until a draw has been made; that is a state, not a failure. */
export function getFixtures(competitionId: string) {
  return request<FixtureSet>(`/competitions/${competitionId}/fixtures`)
}

export function getMatches(competitionId: string) {
  return request<Match[]>(`/competitions/${competitionId}/matches`)
}

export function scheduleMatch(matchId: string, scheduledAt: string, venueId?: string) {
  return request<{ match: Match; warnings: string[] }>(`/matches/${matchId}/schedule`, {
    method: 'POST',
    body: JSON.stringify({ scheduledAt, ...(venueId ? { venueId } : {}) }),
  })
}

export function transitionMatch(matchId: string, action: 'start' | 'postpone' | 'cancel') {
  return request<Match>(`/matches/${matchId}/${action}`, {
    method: 'POST',
    body: JSON.stringify({}),
  })
}

export function recordResult(
  matchId: string,
  payload: {
    outcome: ResultOutcome
    scores?: ScorePayload[]
    winnerParticipantId?: string
    version?: number
  },
) {
  return request<{ matchId: string; status: MatchStatus; version: number; result: ResultSummary }>(
    `/matches/${matchId}/result`,
    { method: 'POST', body: JSON.stringify(payload) },
  )
}

/** 409 LEADERBOARD_NOT_AVAILABLE before anything has been played. */
export function getLeaderboard(competitionId: string) {
  return request<Leaderboard>(`/competitions/${competitionId}/leaderboard`)
}

/**
 * The anonymous view of a draw. Narrower than {@link FixtureSet} on purpose — no venue ids, no
 * optimistic-lock versions, no seeds, and no per-entrant points.
 */
export type PublicFixtures = {
  competitionId: string
  competitionName: string
  generatorKey: FixtureGeneratorKey
  rounds: number
  matchCount: number
  fixtures: PublicRound[]
}

export type PublicRound = {
  round: number
  roundName: string | null
  matches: PublicMatch[]
}

export type PublicMatch = {
  id: string
  status: MatchStatus
  scheduledAt: string | null
  participants: { participantId: string; name: string | null; slot: string | null }[]
  result: {
    outcome: ResultOutcome
    winnerParticipantId: string | null
    participants: {
      participantId: string
      name: string | null
      value: number | null
      unit: string | null
      standing: Standing
    }[]
  } | null
}

/**
 * Anonymous reads, addressed by slug so the tournament's own visibility is the gate. `auth: false`
 * matters: these have to work for a visitor who has never signed in.
 */
export function getPublicFixtures(slug: string, competitionId: string) {
  return request<PublicFixtures>(
    `/public/t/${encodeURIComponent(slug)}/competitions/${competitionId}/fixtures`,
    { auth: false },
  )
}

export function getPublicLeaderboard(slug: string, competitionId: string) {
  return request<Leaderboard>(
    `/public/t/${encodeURIComponent(slug)}/competitions/${competitionId}/leaderboard`,
    { auth: false },
  )
}
