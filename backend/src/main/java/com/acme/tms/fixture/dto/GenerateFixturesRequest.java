package com.acme.tms.fixture.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * @param seedStrategy RANDOM shuffles the approved entrants; SEEDED honours {@code seeds} and puts
 *     anyone left out behind those who were seeded. Defaults to RANDOM.
 */
public record GenerateFixturesRequest(SeedStrategy seedStrategy, List<Seed> seeds) {

    public enum SeedStrategy {
        RANDOM,
        SEEDED
    }

    public record Seed(@NotNull UUID participantId, @NotNull Integer seed) {
    }

    public SeedStrategy strategyOrDefault() {
        return seedStrategy == null ? SeedStrategy.RANDOM : seedStrategy;
    }

    public List<Seed> seedsOrEmpty() {
        return seeds == null ? List.of() : seeds;
    }
}
