package com.acme.tms.result.strategy;

import com.acme.tms.common.exception.ValidationException;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Registry of every deployed {@link LeaderboardStrategy}; see {@code FixtureGeneratorFactory}. */
@Component
public class LeaderboardStrategyFactory {

    private final Map<LeaderboardStrategyKey, LeaderboardStrategy> registry;

    public LeaderboardStrategyFactory(List<LeaderboardStrategy> strategies) {
        Map<LeaderboardStrategyKey, LeaderboardStrategy> byKey = new EnumMap<>(LeaderboardStrategyKey.class);
        for (LeaderboardStrategy strategy : strategies) {
            LeaderboardStrategy clash = byKey.put(strategy.key(), strategy);
            if (clash != null) {
                throw new IllegalStateException(
                    "Duplicate LeaderboardStrategy for key " + strategy.key() + ": "
                        + clash.getClass().getName() + " and " + strategy.getClass().getName()
                );
            }
        }
        this.registry = Map.copyOf(byKey);
    }

    public LeaderboardStrategy get(LeaderboardStrategyKey key) {
        LeaderboardStrategy strategy = registry.get(key);
        if (strategy == null) {
            throw new ValidationException(
                "UNKNOWN_STRATEGY_KEY",
                "No leaderboard strategy is deployed for key " + key + "."
            );
        }
        return strategy;
    }

    public Set<LeaderboardStrategyKey> registeredKeys() {
        return registry.keySet();
    }
}
