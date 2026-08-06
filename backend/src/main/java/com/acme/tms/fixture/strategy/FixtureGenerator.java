package com.acme.tms.fixture.strategy;

import com.acme.tms.common.domain.ParticipantType;

import java.util.Set;

/**
 * Turns approved participants into a fixture plan. Implementations are stateless, thread-safe
 * Spring singletons and are selected purely by {@link #key()} — no caller ever asks which sport it
 * is dealing with.
 *
 * <p>Sprint 3 ships the interface, the registry and the two MVP implementations; the pairing logic
 * itself lands in Sprint 6.
 */
public interface FixtureGenerator {

    FixtureGeneratorKey key();

    /** Formats may be limited to certain participant types; validation rejects a mismatch early. */
    boolean supports(ParticipantType participantType);

    /**
     * Keys this generator requires inside {@code config.rules}, checked when a configuration is
     * saved rather than when fixtures are finally generated.
     */
    default Set<String> requiredRuleKeys() {
        return Set.of();
    }
}
