package com.acme.tms.registration.dto;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Either name an existing {@code participantId} or describe a new {@code participant}; supplying
 * neither is rejected by the service.
 */
public record SubmitRegistrationRequest(
    @NotNull UUID competitionId,
    UUID participantId,
    @Valid ParticipantRequest participant,
    @NotNull JsonNode answers
) {
}
