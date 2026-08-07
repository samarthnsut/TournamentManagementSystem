package com.acme.tms.result.dto;

import com.acme.tms.fixture.domain.MatchStatus;

import java.util.UUID;

public record RecordResultResponse(
    UUID matchId,
    MatchStatus status,
    int version,
    ResultSummaryResponse result
) {
}
