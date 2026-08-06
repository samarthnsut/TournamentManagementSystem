/**
 * Canonical SportConfiguration documents for the sports that ship today.
 *
 * A configuration is an admin-level concept — an organizer adding "Football U16" should not have to
 * think about fixture generators. These presets let the UI create a valid configuration on their
 * behalf, choosing only combinations the backend actually has strategies deployed for. Anything
 * else is rejected at save time with UNKNOWN_STRATEGY_KEY, by design.
 */
export type SportPreset = {
  participantType: 'INDIVIDUAL' | 'TEAM' | 'ORGANIZATION'
  fixtureGenerator: string
  resultEvaluator: string
  leaderboardStrategy: string
  rules: Record<string, unknown>
}

const PRESETS: Record<string, SportPreset> = {
  FOOTBALL: {
    participantType: 'TEAM',
    fixtureGenerator: 'ROUND_ROBIN',
    resultEvaluator: 'POINTS',
    leaderboardStrategy: 'POINTS_TABLE',
    rules: {
      pointsForWin: 3,
      pointsForDraw: 1,
      pointsForLoss: 0,
      legs: 1,
      matchDurationMinutes: 90,
    },
  },
  ATHLETICS_100M: {
    participantType: 'INDIVIDUAL',
    fixtureGenerator: 'NONE',
    resultEvaluator: 'TIME',
    leaderboardStrategy: 'LOWEST_TIME',
    rules: {
      timeUnit: 'SECONDS',
      precision: 3,
      attemptsPerParticipant: 1,
    },
  },
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
