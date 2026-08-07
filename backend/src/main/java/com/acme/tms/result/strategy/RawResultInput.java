package com.acme.tms.result.strategy;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Exactly what the official typed, before any evaluator has looked at it. Kept separate from
 * {@link EvaluatedResult} so the raw submission and the engine's reading of it are both visible
 * when a result is later disputed.
 *
 * @param declaredWinnerParticipantId set only for a walkover, where there is no score to infer a
 *     winner from
 */
public record RawResultInput(
    ResultOutcome outcome,
    List<RawScore> scores,
    UUID declaredWinnerParticipantId
) {

    /**
     * @param value goals, seconds, metres — whatever the evaluator's unit is; null means the
     *     entrant produced no measurable result (did not finish, disqualified)
     */
    public record RawScore(UUID participantId, BigDecimal value, String unit) {
    }
}
