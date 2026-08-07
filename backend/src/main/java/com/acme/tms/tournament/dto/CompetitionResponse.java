package com.acme.tms.tournament.dto;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.fixture.strategy.FixtureGeneratorKey;
import com.acme.tms.result.strategy.LeaderboardStrategyKey;
import com.acme.tms.result.strategy.ResultEvaluatorKey;
import com.acme.tms.tournament.domain.CompetitionStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * @param resultEvaluator the strategy keys are surfaced so a client knows what kind of result entry
 *     to render — a score box per side, or a time per lane. Without them the only way to tell is to
 *     count participants and guess, which is precisely the sport-shaped inference the engine exists
 *     to avoid. Clients still dispatch on the key, never on {@code sportCode}.
 */
public record CompetitionResponse(
    UUID id,
    UUID tournamentId,
    UUID organizationUnitId,
    String name,
    UUID sportId,
    String sportCode,
    UUID sportConfigurationId,
    ParticipantType participantType,
    FixtureGeneratorKey fixtureGenerator,
    ResultEvaluatorKey resultEvaluator,
    LeaderboardStrategyKey leaderboardStrategy,
    CompetitionStatus status,
    Integer maxRegistrations,
    Instant registrationOpenAt,
    Instant registrationCloseAt
) {
}
