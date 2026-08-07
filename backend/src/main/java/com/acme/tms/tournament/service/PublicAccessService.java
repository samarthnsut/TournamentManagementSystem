package com.acme.tms.tournament.service;

import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.Tournament;
import com.acme.tms.tournament.repository.CompetitionRepository;
import com.acme.tms.tournament.repository.TournamentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single gate every anonymous competition read passes through.
 *
 * <p>Public endpoints are addressed by slug *and* competition id, and both have to agree: the
 * tournament must be publicly visible, and the competition must actually belong to it. Taking the
 * competition id on its own would let anyone who guessed or scraped an id read a draft tournament's
 * fixtures, which is precisely the hole the slug is there to close.
 *
 * <p>Every failure is a 404. A 403 would confirm that the thing exists, and an unpublished
 * tournament's existence is itself private (see {@code PublicTournamentService}).
 */
@Service
public class PublicAccessService {

    private final TournamentRepository tournamentRepository;
    private final CompetitionRepository competitionRepository;

    public PublicAccessService(
        TournamentRepository tournamentRepository,
        CompetitionRepository competitionRepository
    ) {
        this.tournamentRepository = tournamentRepository;
        this.competitionRepository = competitionRepository;
    }

    @Transactional(readOnly = true)
    public Competition requirePublicCompetition(String slug, UUID competitionId) {
        Tournament tournament = tournamentRepository.findBySlugAndDeletedAtIsNull(slug)
            .filter(candidate -> candidate.getStatus().isPubliclyVisible())
            .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found."));

        return competitionRepository.findByIdAndDeletedAtIsNull(competitionId)
            .filter(candidate -> candidate.getTournamentId().equals(tournament.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("COMPETITION_NOT_FOUND", "Competition not found."));
    }
}
