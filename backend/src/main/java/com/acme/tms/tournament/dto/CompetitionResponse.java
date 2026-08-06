package com.acme.tms.tournament.dto;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.tournament.domain.CompetitionStatus;

import java.time.Instant;
import java.util.UUID;

public record CompetitionResponse(
    UUID id,
    UUID tournamentId,
    UUID organizationUnitId,
    String name,
    UUID sportId,
    String sportCode,
    UUID sportConfigurationId,
    ParticipantType participantType,
    CompetitionStatus status,
    Integer maxRegistrations,
    Instant registrationOpenAt,
    Instant registrationCloseAt
) {
}
