package com.acme.tms.result.strategy;

import org.springframework.stereotype.Component;

import java.util.Set;

/** A recorded time, lowest wins. Evaluation logic lands in Sprint 6. */
@Component
public class TimeResultEvaluator implements ResultEvaluator {

    @Override
    public ResultEvaluatorKey key() {
        return ResultEvaluatorKey.TIME;
    }

    @Override
    public Set<String> requiredRuleKeys() {
        return Set.of("timeUnit", "precision");
    }
}
