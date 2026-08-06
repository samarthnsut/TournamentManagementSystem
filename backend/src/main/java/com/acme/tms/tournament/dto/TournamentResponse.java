package com.acme.tms.tournament.dto;

import com.acme.tms.common.domain.RegistrationApprovalPolicy;
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
    /** The tournament's own choice, or null when it follows the organization. */
    RegistrationApprovalPolicy approvalPolicy,
    /** What actually applies once inheritance is resolved — what the UI should show. */
    RegistrationApprovalPolicy effectiveApprovalPolicy,
    Instant createdAt
) {
}
