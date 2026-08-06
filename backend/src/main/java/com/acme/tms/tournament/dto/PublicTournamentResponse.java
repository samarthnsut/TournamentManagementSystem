package com.acme.tms.tournament.dto;

import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.tournament.domain.TournamentStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The anonymous view. Deliberately narrower than {@link TournamentResponse}: no owning unit ids, no
 * audit columns, nothing an unauthenticated visitor has no business seeing.
 */
public record PublicTournamentResponse(
    String name,
    String slug,
    TournamentStatus status,
    String description,
    LocalDate startDate,
    LocalDate endDate,
    Organizer organizer,
    List<PublicCompetition> competitions
) {

    public record Organizer(String name, String type) {
    }

    public record PublicCompetition(UUID id, String name, String sportCode, CompetitionStatus status) {
    }
}
