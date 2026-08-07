package com.acme.tms.result.strategy;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One entrant's normalized outcome in one match — the unit of currency between an evaluator and a
 * leaderboard strategy. A leaderboard never re-reads the raw payload, so a new evaluator can be
 * added without any leaderboard learning its input shape.
 *
 * @param value the measured quantity (goals scored, seconds taken); null when there was none
 * @param points what this outcome is worth in a table; zero for evaluators that do not award points
 */
public record ParticipantResult(
    UUID participantId,
    BigDecimal value,
    String unit,
    BigDecimal points,
    Standing standing
) {

    public enum Standing {
        WIN,
        DRAW,
        LOSS,

        /** Placed by measurement rather than by beating an opponent — races, field events. */
        RANKED,

        /** Entered but produced no result: did not start, did not finish, disqualified. */
        NO_CONTEST
    }
}
