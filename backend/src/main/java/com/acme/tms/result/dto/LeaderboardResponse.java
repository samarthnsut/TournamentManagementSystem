package com.acme.tms.result.dto;

import com.acme.tms.result.strategy.LeaderboardStrategyKey;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @param frozen true once the competition is COMPLETED — the board no longer changes (BR-LE-3)
 * @param entries metrics are strategy-shaped: a points table and a timed board share no keys, and
 *     clients render whatever they are given rather than assuming a sport
 */
public record LeaderboardResponse(
    UUID competitionId,
    LeaderboardStrategyKey strategyKey,
    Instant computedAt,
    boolean frozen,
    List<Entry> entries
) {

    public record Entry(int rank, UUID participantId, String name, Map<String, Object> metrics) {
    }
}
