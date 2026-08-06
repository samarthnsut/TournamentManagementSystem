package com.acme.tms.tournament.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record SportConfigurationResponse(
    UUID id,
    UUID organizationUnitId,
    UUID sportId,
    JsonNode config,
    int version,
    boolean isActive,
    Instant createdAt
) {
}
