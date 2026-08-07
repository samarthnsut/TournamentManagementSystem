package com.acme.tms.fixture.strategy;

import com.acme.tms.common.domain.SportRules;

import java.util.List;
import java.util.UUID;

/**
 * Everything a generator is allowed to know. There is deliberately no repository, no competition
 * entity and no sport code here: a generator that cannot look anything up cannot grow a branch on
 * which sport it is running (06 section 4).
 *
 * @param participants entrants holding an APPROVED registration, in draw order
 */
public record FixtureGenerationContext(
    UUID competitionId,
    List<SeededParticipant> participants,
    SportRules rules
) {
}
