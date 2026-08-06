package com.acme.tms.fixture.strategy;

import com.acme.tms.common.domain.ParticipantType;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Everyone plays everyone. Pairing (circle method, bye for odd counts) arrives in Sprint 6; what
 * Sprint 3 needs is for the key to be registered so a Football configuration validates.
 */
@Component
public class RoundRobinFixtureGenerator implements FixtureGenerator {

    @Override
    public FixtureGeneratorKey key() {
        return FixtureGeneratorKey.ROUND_ROBIN;
    }

    @Override
    public boolean supports(ParticipantType participantType) {
        return true;
    }

    @Override
    public Set<String> requiredRuleKeys() {
        return Set.of("legs");
    }
}
