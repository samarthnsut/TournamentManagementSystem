package com.acme.tms.tournament.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateTournamentRequest(
    @Size(max = 200) String name,
    @Pattern(regexp = "^[a-z0-9-]{3,60}$", message = "must be lowercase letters, digits and hyphens")
    @Size(max = 120) String slug,
    String description,
    LocalDate startDate,
    LocalDate endDate
) {
}
