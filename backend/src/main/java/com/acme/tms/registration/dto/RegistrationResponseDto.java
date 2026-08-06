package com.acme.tms.registration.dto;

import com.acme.tms.registration.domain.RegistrationStatus;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

public record RegistrationResponseDto(
    UUID id,
    UUID competitionId,
    RegistrationStatus status,
    ParticipantResponse participant,
    /** The exact form version these answers were validated against — never a newer one. */
    UUID formDefinitionId,
    int formVersion,
    JsonNode answers,
    Instant submittedAt,
    Instant decidedAt,
    Instant withdrawnAt
) {
}
