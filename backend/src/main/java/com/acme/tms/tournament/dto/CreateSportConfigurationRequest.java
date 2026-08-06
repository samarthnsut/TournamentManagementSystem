package com.acme.tms.tournament.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSportConfigurationRequest(
    @NotNull UUID organizationUnitId,
    @NotNull UUID sportId,
    @NotNull JsonNode config
) {
}
