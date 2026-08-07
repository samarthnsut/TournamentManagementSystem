package com.acme.tms.result.strategy;

import com.acme.tms.common.domain.SportRules;

import java.util.Set;

/**
 * Normalizes an official's raw input into a comparable result. Selected by {@link #key()} alone.
 */
public interface ResultEvaluator {

    ResultEvaluatorKey key();

    /**
     * Rejects input this evaluator cannot make sense of — a negative time, a score for someone who
     * is not in the match, a contested result with no scores at all.
     *
     * @throws com.acme.tms.common.exception.ValidationException listing every problem found
     */
    void validate(RawResultInput input, MatchContext match, SportRules rules);

    /**
     * Pure function over already-validated input. Callers run {@link #validate} first; an evaluator
     * may assume its preconditions hold.
     */
    EvaluatedResult evaluate(RawResultInput input, MatchContext match, SportRules rules);

    /** Keys this evaluator requires inside {@code config.rules}, enforced at configuration save. */
    default Set<String> requiredRuleKeys() {
        return Set.of();
    }
}
