package com.acme.tms.fixture.dto;

import com.acme.tms.fixture.domain.MatchStatus;
import com.acme.tms.result.dto.ResultSummaryResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * @param version the value a result submission must echo back; a client that has not refetched
 *     since someone else recorded will be holding a stale one
 */
public record MatchResponse(
    UUID id,
    UUID competitionId,
    UUID fixtureId,
    Integer round,
    MatchStatus status,
    Instant scheduledAt,
    UUID venueId,
    int version,
    List<MatchParticipantResponse> participants,
    ResultSummaryResponse result
) {
}
