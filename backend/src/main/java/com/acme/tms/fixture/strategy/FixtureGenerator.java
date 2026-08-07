package com.acme.tms.fixture.strategy;

import com.acme.tms.common.domain.ParticipantType;

import java.util.Set;

/**
 * Turns approved participants into a fixture plan. Implementations are stateless, thread-safe
 * Spring singletons and are selected purely by {@link #key()} — no caller ever asks which sport it
 * is dealing with.
 */
public interface FixtureGenerator {

    FixtureGeneratorKey key();

    /** Formats may be limited to certain participant types; validation rejects a mismatch early. */
    boolean supports(ParticipantType participantType);

    /**
     * Pure function: approved participants plus rules become a fixture plan. Implementations must
     * not touch a repository, the clock, or any random source not derived from the context — the
     * same context has to produce the same plan, which is what makes regeneration predictable and
     * the generators testable without Spring.
     */
    FixturePlan generate(FixtureGenerationContext context);

    /**
     * Fewest entrants this format can produce anything meaningful from, so an under-subscribed
     * competition fails with a clear message rather than an empty plan.
     */
    default int minimumParticipants() {
        return 2;
    }

    /**
     * Keys this generator requires inside {@code config.rules}, checked when a configuration is
     * saved rather than when fixtures are finally generated.
     */
    default Set<String> requiredRuleKeys() {
        return Set.of();
    }
}
