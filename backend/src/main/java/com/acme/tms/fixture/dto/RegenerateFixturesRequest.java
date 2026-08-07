package com.acme.tms.fixture.dto;

import jakarta.validation.constraints.AssertTrue;

import java.util.List;

/**
 * Regeneration throws away a published draw, so it asks for the word rather than inferring intent
 * from the fact that someone called the endpoint.
 */
public record RegenerateFixturesRequest(
    @AssertTrue(message = "must be true to discard the existing draw") Boolean confirm,
    GenerateFixturesRequest.SeedStrategy seedStrategy,
    List<GenerateFixturesRequest.Seed> seeds
) {

    public GenerateFixturesRequest toGenerateRequest() {
        return new GenerateFixturesRequest(seedStrategy, seeds);
    }
}
