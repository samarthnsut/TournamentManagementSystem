package com.acme.tms.fixture.dto;

import com.acme.tms.fixture.strategy.FixtureGeneratorKey;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The whole draw for a competition.
 *
 * <p>Doc 08 sketches the generate response around a single {@code fixtureId}, which only holds for
 * a one-round format — a round-robin season produces one Fixture row per round. Rounds are
 * therefore returned as a list, each carrying its own id, and the flat counts callers actually
 * display ({@code rounds}, {@code matchCount}) are kept alongside.
 */
public record FixtureSetResponse(
    UUID competitionId,
    FixtureGeneratorKey generatorKey,
    int rounds,
    int matchCount,
    List<RoundResponse> fixtures
) {

    public record RoundResponse(
        UUID fixtureId,
        int round,
        String roundName,
        Instant generatedAt,
        List<MatchResponse> matches
    ) {
    }
}
