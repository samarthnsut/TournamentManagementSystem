package com.acme.tms.fixture.strategy;

import com.acme.tms.common.exception.ValidationException;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Registry of every deployed {@link FixtureGenerator}, indexed by key at startup. Spring injects
 * the implementations, so adding a format never means editing a registration list.
 */
@Component
public class FixtureGeneratorFactory {

    private final Map<FixtureGeneratorKey, FixtureGenerator> registry;

    public FixtureGeneratorFactory(List<FixtureGenerator> generators) {
        Map<FixtureGeneratorKey, FixtureGenerator> byKey = new EnumMap<>(FixtureGeneratorKey.class);
        for (FixtureGenerator generator : generators) {
            FixtureGenerator clash = byKey.put(generator.key(), generator);
            if (clash != null) {
                // Two beans claiming one key would make dispatch order-dependent; refuse to boot.
                throw new IllegalStateException(
                    "Duplicate FixtureGenerator for key " + generator.key() + ": "
                        + clash.getClass().getName() + " and " + generator.getClass().getName()
                );
            }
        }
        this.registry = Map.copyOf(byKey);
    }

    public FixtureGenerator get(FixtureGeneratorKey key) {
        FixtureGenerator generator = registry.get(key);
        if (generator == null) {
            throw new ValidationException(
                "UNKNOWN_STRATEGY_KEY",
                "No fixture generator is deployed for key " + key + "."
            );
        }
        return generator;
    }

    /** Drives configuration validation and the admin UI's dropdowns. */
    public Set<FixtureGeneratorKey> registeredKeys() {
        return registry.keySet();
    }
}
