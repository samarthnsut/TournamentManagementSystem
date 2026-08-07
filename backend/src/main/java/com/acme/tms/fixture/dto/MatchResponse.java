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
    /** Denormalized from the competition: doc 08 §1.6, and the audit trail needs an owning tenant. */
    UUID organizationUnitId,
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
