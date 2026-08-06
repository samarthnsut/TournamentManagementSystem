package com.acme.tms.result.strategy;

import com.acme.tms.common.exception.ValidationException;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry of every deployed {@link ResultEvaluator}; see {@code FixtureGeneratorFactory}. */
@Component
public class ResultEvaluatorFactory {

    private final Map<ResultEvaluatorKey, ResultEvaluator> registry;

    public ResultEvaluatorFactory(List<ResultEvaluator> evaluators) {
        Map<ResultEvaluatorKey, ResultEvaluator> byKey = new EnumMap<>(ResultEvaluatorKey.class);
        for (ResultEvaluator evaluator : evaluators) {
            ResultEvaluator clash = byKey.put(evaluator.key(), evaluator);
            if (clash != null) {
                throw new IllegalStateException(
                    "Duplicate ResultEvaluator for key " + evaluator.key() + ": "
                        + clash.getClass().getName() + " and " + evaluator.getClass().getName()
                );
            }
        }
        this.registry = Map.copyOf(byKey);
    }

    public ResultEvaluator get(ResultEvaluatorKey key) {
        ResultEvaluator evaluator = registry.get(key);
        if (evaluator == null) {
            throw new ValidationException(
                "UNKNOWN_STRATEGY_KEY",
                "No result evaluator is deployed for key " + key + "."
            );
        }
        return evaluator;
    }

    public Set<ResultEvaluatorKey> registeredKeys() {
        return registry.keySet();
    }
}
