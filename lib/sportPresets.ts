/**
 * Canonical SportConfiguration documents for the sports that ship today.
 *
 * A configuration is an admin-level concept — an organizer adding "Football U16" should not have to
 * think about fixture generators. These presets let the UI create a valid configuration on their
 * behalf, choosing only combinations the backend actually has strategies deployed for. Anything
 * else is rejected at save time with UNKNOWN_STRATEGY_KEY, by design.
 *
 * Every sport below is served by strategies that already exist: nothing here required new code,
 * which is the whole point of the strategy registry.
 */
export type SportPreset = {
  participantType: 'INDIVIDUAL' | 'TEAM' | 'ORGANIZATION'
  fixtureGenerator: string
  resultEvaluator: string
  leaderboardStrategy: string
  rules: Record<string, unknown>
}

/** A league: everyone plays everyone, three points for a win. */
function league(
  participantType: SportPreset['participantType'],
  extraRules: Record<string, unknown> = {},
): SportPreset {
  return {
    participantType,
    fixtureGenerator: 'ROUND_ROBIN',
    resultEvaluator: 'POINTS',
    leaderboardStrategy: 'POINTS_TABLE',
    rules: {
      pointsForWin: 3,
      pointsForDraw: 1,
      pointsForLoss: 0,
      legs: 1,
      tiebreakers: ['SCORE_DIFFERENCE', 'SCORE_FOR', 'HEAD_TO_HEAD'],
      ...extraRules,
    },
  }
}

/** A measured event: no pairing, lowest time wins. */
function timed(precision = 2, extraRules: Record<string, unknown> = {}): SportPreset {
  return {
    participantType: 'INDIVIDUAL',
    fixtureGenerator: 'NONE',
    resultEvaluator: 'TIME',
    leaderboardStrategy: 'LOWEST_TIME',
    rules: {
      timeUnit: 'SECONDS',
      precision,
      attemptsPerParticipant: 1,
      ...extraRules,
    },
  }
}

const PRESETS: Record<string, SportPreset> = {
  // Team leagues.
  FOOTBALL: league('TEAM', { matchDurationMinutes: 90, teamSize: { min: 7, max: 18 } }),
  CRICKET: league('TEAM', { oversPerInnings: 20, teamSize: { min: 11, max: 15 } }),
  BASKETBALL: league('TEAM', { matchDurationMinutes: 40, teamSize: { min: 5, max: 12 } }),
  HOCKEY: league('TEAM', { matchDurationMinutes: 60, teamSize: { min: 11, max: 16 } }),
  VOLLEYBALL: league('TEAM', { setsToWin: 3, teamSize: { min: 6, max: 12 } }),
  KABADDI: league('TEAM', { matchDurationMinutes: 40, teamSize: { min: 7, max: 12 } }),
  HANDBALL: league('TEAM', { matchDurationMinutes: 60, teamSize: { min: 7, max: 14 } }),

  // Individual head-to-head — still a league, one player a side.
  BADMINTON_SINGLES: league('INDIVIDUAL', { setsToWin: 2 }),
  TABLE_TENNIS_SINGLES: league('INDIVIDUAL', { setsToWin: 4 }),
  TENNIS_SINGLES: league('INDIVIDUAL', { setsToWin: 2 }),
  // Half a point for a draw is the reason `points` is a decimal all the way through.
  CHESS: {
    participantType: 'INDIVIDUAL',
    fixtureGenerator: 'ROUND_ROBIN',
    resultEvaluator: 'POINTS',
    leaderboardStrategy: 'POINTS_TABLE',
    rules: {
      pointsForWin: 1,
      pointsForDraw: 0.5,
      pointsForLoss: 0,
      legs: 1,
      tiebreakers: ['HEAD_TO_HEAD', 'WINS'],
    },
  },

  // Measured events. Sprints are timed to the millisecond; distance events are not.
  ATHLETICS_100M: timed(3),
  ATHLETICS_200M: timed(3),
  ATHLETICS_400M: timed(2),
  ATHLETICS_800M: timed(2),
  ATHLETICS_1500M: timed(2),
  ATHLETICS_5000M: timed(1),
  ATHLETICS_MARATHON: timed(0),
  SWIMMING_50M_FREESTYLE: timed(2),
  SWIMMING_100M_FREESTYLE: timed(2),
  CYCLING_TIME_TRIAL: timed(1),
}

export function hasPresetFor(sportCode: string) {
  return sportCode in PRESETS
}

/** The full config document to POST for a sport, or null when we have no preset for it. */
export function buildConfigFor(sportCode: string): Record<string, unknown> | null {
  const preset = PRESETS[sportCode]
  if (!preset) {
    return null
  }
  return { sport: sportCode, ...preset }
}

/** What an organizer needs to know about a sport before picking it. */
export function summarizePreset(sportCode: string): string | null {
  const preset = PRESETS[sportCode]
  if (!preset) {
    return null
  }
  return preset.fixtureGenerator === 'NONE'
    ? 'Individual timed event — one final, fastest wins'
    : `${preset.participantType === 'TEAM' ? 'Teams' : 'Players'} play everyone once — points table`
}
