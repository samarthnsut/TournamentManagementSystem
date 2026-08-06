package com.acme.tms.result.strategy;

import java.util.Set;

/**
 * Ranks a competition's evaluated results. Selected by {@link #key()} alone.
 *
 * <p>Sprint 3 ships the interface, the registry and the MVP implementations; ranking and
 * tie-breaking land in Sprint 6.
 */
public interface LeaderboardStrategy {

    LeaderboardStrategyKey key();

    /** Fixture formats this ranking makes sense for — the coherence check in doc 06 section 7.5. */
    Set<String> compatibleFixtureGenerators();

    /** Keys this strategy requires inside {@code config.rules}, enforced at configuration save. */
    default Set<String> requiredRuleKeys() {
        return Set.of();
    }
}
