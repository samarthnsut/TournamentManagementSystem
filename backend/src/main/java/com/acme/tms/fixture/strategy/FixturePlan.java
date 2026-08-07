package com.acme.tms.fixture.strategy;

import java.util.List;
import java.util.UUID;

/**
 * The pure output of a {@link FixtureGenerator}: rounds, the matches in them, and who occupies
 * which slot. Nothing here has an identity yet — {@code FixtureService} persists the plan, which
 * is what keeps generators unit-testable without a database.
 */
public record FixturePlan(List<PlannedRound> rounds) {

    public static FixturePlan empty() {
        return new FixturePlan(List.of());
    }

    public int matchCount() {
        return rounds.stream().mapToInt(round -> round.matches().size()).sum();
    }

    public record PlannedRound(int roundNumber, String roundName, List<PlannedMatch> matches) {
    }

    public record PlannedMatch(List<PlannedSlot> slots) {
    }

    /**
     * @param slot HOME/AWAY for a head-to-head pairing, LANE_n for a race — a label for humans and
     *     for result entry, never something the engine branches on
     */
    public record PlannedSlot(UUID participantId, String slot, Integer seed) {
    }
}
