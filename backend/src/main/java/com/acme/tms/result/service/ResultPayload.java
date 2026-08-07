package com.acme.tms.result.service;

import com.acme.tms.result.strategy.EvaluatedResult;
import com.acme.tms.result.strategy.RawResultInput;

/**
 * What gets written to {@code result.payload}: the official's submission exactly as it arrived,
 * and the evaluator's reading of it.
 *
 * <p>Both halves are kept because they answer different questions. The raw half is the evidence
 * when a result is disputed; the evaluated half is what leaderboards are rebuilt from, and keeping
 * it means a later change to a tenant's points rules does not silently rewrite last season.
 */
public record ResultPayload(RawResultInput raw, EvaluatedResult evaluation) {
}
