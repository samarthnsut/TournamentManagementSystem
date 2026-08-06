package com.acme.tms.registration.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record FormDefinitionResponse(
    UUID id,
    UUID competitionId,
    int version,
    JsonNode schema,
    boolean isActive,
    Instant createdAt
) {
}
