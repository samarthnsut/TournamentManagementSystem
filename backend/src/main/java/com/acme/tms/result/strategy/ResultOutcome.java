package com.acme.tms.result.strategy;

/**
 * What the official is declaring when they record a result. Deliberately narrower than
 * {@code MatchStatus}: only these two produce a Result row (BR-M-2), and a cancelled match never
 * reaches an evaluator at all.
 */
public enum ResultOutcome {

    /** The match was contested and the scores below describe it. */
    COMPLETED,

    /** One side did not appear; the declared winner takes it uncontested. */
    WALKOVER
}
