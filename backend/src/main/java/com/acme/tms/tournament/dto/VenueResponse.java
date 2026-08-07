package com.acme.tms.tournament.dto;

import java.time.Instant;
import java.util.UUID;

public record VenueResponse(
    UUID id,
    UUID organizationUnitId,
    String name,
    String addressLine,
    String city,
    String state,
    Integer capacity,
    Instant createdAt
) {
}
