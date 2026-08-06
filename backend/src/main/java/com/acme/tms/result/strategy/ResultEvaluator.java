package com.acme.tms.result.strategy;

import java.util.Set;

/**
 * Normalizes an official's raw input into a comparable result. Selected by {@link #key()} alone.
 *
 * <p>Sprint 3 ships the interface, the registry and the MVP implementations; evaluation logic
 * lands in Sprint 6.
 */
public interface ResultEvaluator {

    ResultEvaluatorKey key();

    /** Keys this evaluator requires inside {@code config.rules}, enforced at configuration save. */
    default Set<String> requiredRuleKeys() {
        return Set.of();
    }
}
