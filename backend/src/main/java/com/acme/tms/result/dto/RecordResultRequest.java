package com.acme.tms.result.dto;

import com.acme.tms.result.strategy.ResultOutcome;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What an official submits. The shape of {@code scores} is deliberately uniform across sports — a
 * value per participant — and it is the competition's ResultEvaluator, not this record, that
 * decides whether a value is goals, seconds or metres.
 *
 * @param version the match version the official was looking at; a mismatch means someone else got
 *     there first and the submission is rejected rather than silently overwriting theirs
 */
public record RecordResultRequest(
    @NotNull ResultOutcome outcome,
    List<Score> scores,
    UUID winnerParticipantId,
    Integer version
) {

    public record Score(@NotNull UUID participantId, BigDecimal value, String unit) {
    }

    public List<Score> scoresOrEmpty() {
        return scores == null ? List.of() : scores;
    }
}
