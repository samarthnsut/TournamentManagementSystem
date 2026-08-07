package com.acme.tms.result.dto;

import com.acme.tms.result.strategy.ParticipantResult;
import com.acme.tms.result.strategy.ResultEvaluatorKey;
import com.acme.tms.result.strategy.ResultOutcome;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The evaluated result as it appears on a match — raw scores plus what the engine made of them. */
public record ResultSummaryResponse(
    UUID resultId,
    ResultEvaluatorKey evaluatorKey,
    ResultOutcome outcome,
    UUID winnerParticipantId,
    Instant recordedAt,
    List<ParticipantOutcomeResponse> participants
) {

    public record ParticipantOutcomeResponse(
        UUID participantId,
        String name,
        BigDecimal value,
        String unit,
        BigDecimal points,
        ParticipantResult.Standing standing
    ) {
    }
}
