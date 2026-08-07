package com.acme.tms.result.strategy;

import com.acme.tms.common.domain.SportRules;

import java.util.List;
import java.util.Set;

/** Ranks a competition's evaluated results. Selected by {@link #key()} alone. */
public interface LeaderboardStrategy {

    LeaderboardStrategyKey key();

    /** Fixture formats this ranking makes sense for — the coherence check in doc 06 section 7.5. */
    Set<String> compatibleFixtureGenerators();

    /**
     * Pure and idempotent (BR-LE-2): the same results must always produce the same ordering, since
     * the whole board is recomputed from scratch on every result confirmation.
     */
    List<LeaderboardRow> rank(CompetitionResults results, SportRules rules);

    /** Keys this strategy requires inside {@code config.rules}, enforced at configuration save. */
    default Set<String> requiredRuleKeys() {
        return Set.of();
    }
}
