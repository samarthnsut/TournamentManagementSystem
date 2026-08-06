package com.acme.tms.tournament.dto;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record UpdateCompetitionRequest(
    @Size(max = 200) String name,
    @Positive Integer maxRegistrations,
    Instant registrationOpenAt,
    Instant registrationCloseAt
) {
}
