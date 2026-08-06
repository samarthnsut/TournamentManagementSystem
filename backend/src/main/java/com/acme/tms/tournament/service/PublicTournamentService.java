package com.acme.tms.tournament.service;

import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.organization.domain.OrganizationUnit;
import com.acme.tms.organization.repository.OrganizationUnitRepository;
import com.acme.tms.tournament.domain.Sport;
import com.acme.tms.tournament.domain.Tournament;
import com.acme.tms.tournament.dto.PublicTournamentResponse;
import com.acme.tms.tournament.repository.CompetitionRepository;
import com.acme.tms.tournament.repository.SportRepository;
import com.acme.tms.tournament.repository.TournamentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Backs the anonymous {@code /t/{slug}} page. Nothing here consults the caller's identity, so it
 * must be conservative by construction: an unpublished tournament is reported as missing rather
 * than forbidden, which keeps a draft's existence — and its slug — private.
 */
@Service
public class PublicTournamentService {

    private final TournamentRepository tournamentRepository;
    private final CompetitionRepository competitionRepository;
    private final SportRepository sportRepository;
    private final OrganizationUnitRepository organizationUnitRepository;

    public PublicTournamentService(
        TournamentRepository tournamentRepository,
        CompetitionRepository competitionRepository,
        SportRepository sportRepository,
        OrganizationUnitRepository organizationUnitRepository
    ) {
        this.tournamentRepository = tournamentRepository;
        this.competitionRepository = competitionRepository;
        this.sportRepository = sportRepository;
        this.organizationUnitRepository = organizationUnitRepository;
    }

    @Transactional(readOnly = true)
    public PublicTournamentResponse getBySlug(String slug) {
        Tournament tournament = tournamentRepository.findBySlugAndDeletedAtIsNull(slug)
            .filter(candidate -> candidate.getStatus().isPubliclyVisible())
            .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found."));

        PublicTournamentResponse.Organizer organizer = organizationUnitRepository
            .findByIdAndDeletedAtIsNull(tournament.getOrganizationUnitId())
            .map(this::toOrganizer)
            .orElse(null);

        List<PublicTournamentResponse.PublicCompetition> competitions = competitionRepository
            .findByTournamentIdAndDeletedAtIsNullOrderByCreatedAtAsc(tournament.getId())
            .stream()
            .map(competition -> new PublicTournamentResponse.PublicCompetition(
                competition.getId(),
                competition.getName(),
                sportRepository.findByIdAndDeletedAtIsNull(competition.getSportId())
                    .map(Sport::getCode)
                    .orElse(null),
                competition.getStatus()
            ))
            .toList();

        return new PublicTournamentResponse(
            tournament.getName(),
            tournament.getSlug(),
            tournament.getStatus(),
            tournament.getDescription(),
            tournament.getStartDate(),
            tournament.getEndDate(),
            organizer,
            competitions
        );
    }

    private PublicTournamentResponse.Organizer toOrganizer(OrganizationUnit unit) {
        return new PublicTournamentResponse.Organizer(unit.getName(), unit.getType().name());
    }
}
