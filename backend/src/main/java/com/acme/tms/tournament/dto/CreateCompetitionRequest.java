package com.acme.tms.tournament.dto;

import com.acme.tms.common.domain.ParticipantType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record CreateCompetitionRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull UUID sportConfigurationId,
    /** Optional cross-check: when supplied it must match the configuration's own participantType. */
    ParticipantType participantType,
    @Positive Integer maxRegistrations,
    Instant registrationOpenAt,
    Instant registrationCloseAt
) {
}
