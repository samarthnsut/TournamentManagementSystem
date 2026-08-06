package com.acme.tms.tournament.service;

import com.acme.tms.common.domain.ParticipantType;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.tournament.domain.Sport;
import com.acme.tms.tournament.domain.SportConfiguration;
import com.acme.tms.tournament.domain.Tournament;
import com.acme.tms.tournament.domain.TournamentStatus;
import com.acme.tms.tournament.dto.CompetitionResponse;
import com.acme.tms.tournament.dto.CreateCompetitionRequest;
import com.acme.tms.tournament.dto.TransitionResponse;
import com.acme.tms.tournament.dto.UpdateCompetitionRequest;
import com.acme.tms.tournament.repository.CompetitionRepository;
import com.acme.tms.tournament.repository.SportConfigurationRepository;
import com.acme.tms.tournament.repository.SportRepository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
public class CompetitionService {

    private static final EnumSet<TournamentStatus> CLOSED_TO_NEW_COMPETITIONS =
        EnumSet.of(TournamentStatus.COMPLETED, TournamentStatus.CANCELLED, TournamentStatus.ARCHIVED);

    private final CompetitionRepository competitionRepository;
    private final SportConfigurationRepository sportConfigurationRepository;
    private final SportRepository sportRepository;
    private final TournamentService tournamentService;
    private final SportConfigurationValidator sportConfigurationValidator;

    public CompetitionService(
        CompetitionRepository competitionRepository,
        SportConfigurationRepository sportConfigurationRepository,
        SportRepository sportRepository,
        TournamentService tournamentService,
        SportConfigurationValidator sportConfigurationValidator
    ) {
        this.competitionRepository = competitionRepository;
        this.sportConfigurationRepository = sportConfigurationRepository;
        this.sportRepository = sportRepository;
        this.tournamentService = tournamentService;
        this.sportConfigurationValidator = sportConfigurationValidator;
    }

    @Transactional
    public CompetitionResponse create(UUID tournamentId, CreateCompetitionRequest request) {
        Tournament tournament = tournamentService.require(tournamentId);
        if (CLOSED_TO_NEW_COMPETITIONS.contains(tournament.getStatus())) {
            throw new ConflictException(
                "TOURNAMENT_NOT_EDITABLE",
                "Competitions cannot be added to a " + tournament.getStatus() + " tournament."
            );
        }

        SportConfiguration configuration = requireConfiguration(request.sportConfigurationId());
        JsonNode config = sportConfigurationValidator.parse(configuration.getConfig());
        ParticipantType configuredType = ParticipantType.valueOf(config.get("participantType").asText());

        // The request may state the participant type; when it does it must agree with the config,
        // which is the single source of truth.
        if (request.participantType() != null && request.participantType() != configuredType) {
            throw new ValidationException(
                "PARTICIPANT_TYPE_MISMATCH",
                "participantType " + request.participantType() + " does not match the sport configuration's "
                    + configuredType + "."
            );
        }

        Competition competition = new Competition();
        competition.setTournamentId(tournament.getId());
        // Ownership always follows the tournament so scope checks cannot be sidestepped.
        competition.setOrganizationUnitId(tournament.getOrganizationUnitId());
        competition.setSportId(configuration.getSportId());
        competition.setSportConfigurationId(configuration.getId());
        competition.setName(request.name().trim());
        competition.setMaxRegistrations(request.maxRegistrations());
        competition.setRegistrationOpenAt(request.registrationOpenAt());
        competition.setRegistrationCloseAt(request.registrationCloseAt());
        competition.setStatus(CompetitionStatus.DRAFT);

        return toResponse(competitionRepository.save(competition), configuredType);
    }

    @Transactional(readOnly = true)
    public List<CompetitionResponse> listForTournament(UUID tournamentId) {
        tournamentService.require(tournamentId);
        return competitionRepository.findByTournamentIdAndDeletedAtIsNullOrderByCreatedAtAsc(tournamentId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public CompetitionResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    public CompetitionResponse update(UUID id, UpdateCompetitionRequest request) {
        Competition competition = require(id);
        if (competition.getStatus().isTerminal()) {
            throw new ConflictException(
                "COMPETITION_NOT_EDITABLE",
                "A " + competition.getStatus() + " competition is read-only."
            );
        }

        if (request.name() != null && !request.name().isBlank()) {
            competition.setName(request.name().trim());
        }
        if (request.maxRegistrations() != null) {
            competition.setMaxRegistrations(request.maxRegistrations());
        }
        if (request.registrationOpenAt() != null) {
            competition.setRegistrationOpenAt(request.registrationOpenAt());
        }
        if (request.registrationCloseAt() != null) {
            competition.setRegistrationCloseAt(request.registrationCloseAt());
        }

        return toResponse(competition);
    }

    @Transactional
    public TransitionResponse transition(UUID id, CompetitionStatus target) {
        Competition competition = require(id);
        CompetitionStatus current = competition.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new ConflictException(
                "INVALID_STATE_TRANSITION",
                "Cannot transition competition from " + current + " to " + target + "."
            );
        }

        // A competition cannot take entries before its tournament is at least published.
        if (target == CompetitionStatus.OPEN) {
            Tournament tournament = tournamentService.require(competition.getTournamentId());
            if (tournament.getStatus() == TournamentStatus.DRAFT) {
                throw new ConflictException(
                    "TOURNAMENT_NOT_PUBLISHED",
                    "Publish the tournament before opening a competition."
                );
            }
        }

        competition.setStatus(target);
        return new TransitionResponse(competition.getId(), target.name(), Instant.now());
    }

    public Competition require(UUID id) {
        return competitionRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("COMPETITION_NOT_FOUND", "Competition not found."));
    }

    private SportConfiguration requireConfiguration(UUID id) {
        return sportConfigurationRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException(
                "SPORT_CONFIGURATION_NOT_FOUND", "Sport configuration not found."));
    }

    private CompetitionResponse toResponse(Competition competition) {
        SportConfiguration configuration = sportConfigurationRepository
            .findByIdAndDeletedAtIsNull(competition.getSportConfigurationId())
            .orElse(null);
        ParticipantType participantType = null;
        if (configuration != null) {
            JsonNode config = sportConfigurationValidator.parse(configuration.getConfig());
            participantType = ParticipantType.valueOf(config.get("participantType").asText());
        }
        return toResponse(competition, participantType);
    }

    private CompetitionResponse toResponse(Competition competition, ParticipantType participantType) {
        String sportCode = sportRepository.findByIdAndDeletedAtIsNull(competition.getSportId())
            .map(Sport::getCode)
            .orElse(null);

        return new CompetitionResponse(
            competition.getId(),
            competition.getTournamentId(),
            competition.getOrganizationUnitId(),
            competition.getName(),
            competition.getSportId(),
            sportCode,
            competition.getSportConfigurationId(),
            participantType,
            competition.getStatus(),
            competition.getMaxRegistrations(),
            competition.getRegistrationOpenAt(),
            competition.getRegistrationCloseAt()
        );
    }
}
