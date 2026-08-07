package com.acme.tms.result.strategy;

import java.util.List;
import java.util.UUID;

/**
 * An evaluator's reading of one match: who won, and what each entrant walks away with. Persisted
 * as the Result row's payload, which is what makes leaderboards recomputable from stored data
 * without re-running the evaluator (BR-LE-2).
 *
 * @param winnerParticipantId null for a draw, and for events where "winner" is not meaningful
 */
public record EvaluatedResult(UUID winnerParticipantId, List<ParticipantResult> participants) {
}
