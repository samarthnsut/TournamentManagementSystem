package com.acme.tms.fixture.strategy;

import com.acme.tms.common.domain.ParticipantType;

import org.springframework.stereotype.Component;

/**
 * For measured events such as a 100m final, where there is nothing to pair. It still produces the
 * fixture shell in Sprint 6 so downstream result code never has to special-case "no fixtures".
 */
@Component
public class NoneFixtureGenerator implements FixtureGenerator {

    @Override
    public FixtureGeneratorKey key() {
        return FixtureGeneratorKey.NONE;
    }

    @Override
    public boolean supports(ParticipantType participantType) {
        return true;
    }
}
