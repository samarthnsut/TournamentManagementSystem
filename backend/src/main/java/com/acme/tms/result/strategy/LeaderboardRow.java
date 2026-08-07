package com.acme.tms.result.strategy;

import java.util.Map;
import java.util.UUID;

/**
 * One ranked line of a leaderboard.
 *
 * @param rank competition ranking — genuinely tied entrants share a rank and the next rank skips
 *     accordingly (1, 1, 3), so a reader can tell a tie from an arbitrary ordering
 * @param metrics strategy-shaped and insertion-ordered, persisted verbatim as the entry's JSONB
 */
public record LeaderboardRow(UUID participantId, int rank, Map<String, Object> metrics) {
}
