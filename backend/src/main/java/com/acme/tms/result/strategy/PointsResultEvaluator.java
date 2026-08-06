package com.acme.tms.result.strategy;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Win/draw/loss converted to points using {@code rules}. Evaluation logic lands in Sprint 6. */
@Component
public class PointsResultEvaluator implements ResultEvaluator {

    @Override
    public ResultEvaluatorKey key() {
        return ResultEvaluatorKey.POINTS;
    }

    @Override
    public Set<String> requiredRuleKeys() {
        return Set.of("pointsForWin", "pointsForDraw", "pointsForLoss");
    }
}
