package com.acme.tms.result.strategy;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Fastest recorded time first, for measured events. Ranking lands in Sprint 6. */
@Component
public class LowestTimeLeaderboard implements LeaderboardStrategy {

    @Override
    public LeaderboardStrategyKey key() {
        return LeaderboardStrategyKey.LOWEST_TIME;
    }

    @Override
    public Set<String> compatibleFixtureGenerators() {
        return Set.of("NONE", "ROUND_ROBIN");
    }
}
