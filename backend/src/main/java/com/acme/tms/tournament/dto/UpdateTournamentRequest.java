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
    LocalDate endDate,
    /**
     * {@code AUTO_APPROVE}, {@code DIRECT_SINGLE_APPROVAL}, or {@code INHERIT} to clear the
     * override and follow the organization again. A JSON null cannot express "clear" in a PATCH,
     * hence the explicit sentinel.
     */
    String approvalPolicy
) {
}
