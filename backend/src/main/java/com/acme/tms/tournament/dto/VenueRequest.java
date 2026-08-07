package com.acme.tms.tournament.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Used for create and update; on update the organization unit is ignored — venues do not move. */
public record VenueRequest(
    @NotNull UUID organizationUnitId,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 300) String addressLine,
    @Size(max = 100) String city,
    @Size(max = 100) String state,
    @Positive Integer capacity
) {
}
