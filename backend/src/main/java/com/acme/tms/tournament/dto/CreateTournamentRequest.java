package com.acme.tms.tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

public record CreateTournamentRequest(
    @NotNull UUID organizationUnitId,
    @NotBlank @Size(max = 200) String name,
    // Optional: omitted slugs are generated from the name.
    @Pattern(regexp = "^[a-z0-9-]{3,60}$", message = "must be lowercase letters, digits and hyphens")
    @Size(max = 120) String slug,
    String description,
    LocalDate startDate,
    LocalDate endDate
) {
}
