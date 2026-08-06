package com.acme.tms.result.strategy;

import org.springframework.stereotype.Component;

import java.util.Set;

/** League table ordered on points, then the configured tiebreakers. Ranking lands in Sprint 6. */
@Component
public class PointsTableLeaderboard implements LeaderboardStrategy {

    @Override
    public LeaderboardStrategyKey key() {
        return LeaderboardStrategyKey.POINTS_TABLE;
    }

    @Override
    public Set<String> compatibleFixtureGenerators() {
        return Set.of("ROUND_ROBIN", "SWISS");
    }
}
