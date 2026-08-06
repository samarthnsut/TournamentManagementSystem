package com.acme.tms.tournament.dto;

import com.acme.tms.tournament.domain.TournamentStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TournamentResponse(
    UUID id,
    UUID organizationUnitId,
    String name,
    String slug,
    String description,
    TournamentStatus status,
    LocalDate startDate,
    LocalDate endDate,
    Instant publishedAt,
    Instant createdAt
) {
}
