package com.acme.tms.fixture.dto;

import com.acme.tms.fixture.domain.MatchStatus;
import com.acme.tms.fixture.strategy.FixtureGeneratorKey;
import com.acme.tms.result.strategy.ParticipantResult;
import com.acme.tms.result.strategy.ResultOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The anonymous view of a draw. Deliberately narrower than {@link FixtureSetResponse}, on the same
 * principle as {@code PublicTournamentResponse}: no venue ids, no optimistic-lock versions, no
 * fixture ids and no seeds — organizer plumbing a spectator has no business seeing.
 *
 * <p>Points are dropped from the per-entrant outcome too: what a result was worth belongs on the
 * standings, and repeating it against every match only invites the two to disagree.
 */
public record PublicFixturesResponse(
    UUID competitionId,
    String competitionName,
    FixtureGeneratorKey generatorKey,
    int rounds,
    int matchCount,
    List<PublicRound> fixtures
) {

    public record PublicRound(int round, String roundName, List<PublicMatch> matches) {
    }

    public record PublicMatch(
        UUID id,
        MatchStatus status,
        Instant scheduledAt,
        List<PublicMatchParticipant> participants,
        PublicResult result
    ) {
    }

    public record PublicMatchParticipant(UUID participantId, String name, String slot) {
    }

    public record PublicResult(
        ResultOutcome outcome,
        UUID winnerParticipantId,
        List<PublicOutcome> participants
    ) {
    }

    public record PublicOutcome(
        UUID participantId,
        String name,
        BigDecimal value,
        String unit,
        ParticipantResult.Standing standing
    ) {
    }
}
