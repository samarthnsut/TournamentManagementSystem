package com.acme.tms.tournament.service;

import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.tournament.domain.Competition;
import com.acme.tms.tournament.domain.CompetitionStatus;
import com.acme.tms.tournament.domain.Tournament;
import com.acme.tms.tournament.domain.TournamentStatus;
import com.acme.tms.tournament.dto.CreateTournamentRequest;
import com.acme.tms.tournament.dto.TournamentResponse;
import com.acme.tms.tournament.dto.TransitionResponse;
import com.acme.tms.tournament.dto.UpdateTournamentRequest;
import com.acme.tms.tournament.repository.CompetitionRepository;
import com.acme.tms.tournament.repository.TournamentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TournamentService {

    private final TournamentRepository tournamentRepository;
    private final CompetitionRepository competitionRepository;
    private final SlugService slugService;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public TournamentService(
        TournamentRepository tournamentRepository,
        CompetitionRepository competitionRepository,
        SlugService slugService,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser
    ) {
        this.tournamentRepository = tournamentRepository;
        this.competitionRepository = competitionRepository;
        this.slugService = slugService;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    @Transactional
    public TournamentResponse create(CreateTournamentRequest request) {
        validateDates(request.startDate(), request.endDate());

        Tournament tournament = new Tournament();
        tournament.setOrganizationUnitId(request.organizationUnitId());
        tournament.setName(request.name().trim());
        tournament.setSlug(slugService.resolve(request.slug(), request.name()));
        tournament.setDescription(request.description());
        tournament.setStartDate(request.startDate());
        tournament.setEndDate(request.endDate());
        tournament.setStatus(TournamentStatus.DRAFT);

        return toResponse(tournamentRepository.save(tournament));
    }

    @Transactional(readOnly = true)
    public TournamentResponse get(UUID id) {
        return toResponse(require(id));
    }

    /** Lists tournaments across every organization unit the caller can read. */
    @Transactional(readOnly = true)
    public List<TournamentResponse> list(TournamentStatus status) {
        List<UUID> visibleUnitIds =
            scopeEvaluator.visibleOrganizationUnitIds(currentUser.requireUserId(), "tournament:read");
        if (visibleUnitIds.isEmpty()) {
            return List.of();
        }

        List<Tournament> tournaments = status == null
            ? tournamentRepository.findByOrganizationUnitIdInAndDeletedAtIsNullOrderByCreatedAtDesc(visibleUnitIds)
            : tournamentRepository.findByOrganizationUnitIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
                visibleUnitIds, status);

        return tournaments.stream().map(this::toResponse).toList();
    }

    @Transactional
    public TournamentResponse update(UUID id, UpdateTournamentRequest request) {
        Tournament tournament = require(id);
        if (tournament.getStatus().isTerminal()) {
            throw new ConflictException("TOURNAMENT_NOT_EDITABLE", "An archived tournament is read-only.");
        }

        if (request.name() != null && !request.name().isBlank()) {
            tournament.setName(request.name().trim());
        }
        if (request.description() != null) {
            tournament.setDescription(request.description());
        }
        if (request.slug() != null && !request.slug().equals(tournament.getSlug())) {
            // The slug is a public URL; once anyone could have linked to it, it is frozen (BR-T-1).
            if (!tournament.isSlugMutable()) {
                throw new ConflictException(
                    "SLUG_IMMUTABLE",
                    "The slug cannot change once a tournament is published."
                );
            }
            tournament.setSlug(slugService.resolve(request.slug(), request.name()));
        }

        // Dates are frozen once play has begun (BR-T-6).
        if (request.startDate() != null || request.endDate() != null) {
            if (tournament.getStatus() == TournamentStatus.IN_PROGRESS
                || tournament.getStatus() == TournamentStatus.COMPLETED) {
                throw new ConflictException(
                    "DATES_LOCKED",
                    "Dates cannot change once the tournament is in progress."
                );
            }
            java.time.LocalDate start = request.startDate() != null ? request.startDate() : tournament.getStartDate();
            java.time.LocalDate end = request.endDate() != null ? request.endDate() : tournament.getEndDate();
            validateDates(start, end);
            tournament.setStartDate(start);
            tournament.setEndDate(end);
        }

        return toResponse(tournament);
    }

    /** Soft delete, permitted only while nothing downstream can depend on it. */
    @Transactional
    public void delete(UUID id) {
        Tournament tournament = require(id);
        if (tournament.getStatus() != TournamentStatus.DRAFT) {
            throw new ConflictException("TOURNAMENT_NOT_DELETABLE", "Only a DRAFT tournament can be deleted.");
        }
        tournament.markDeleted();
    }

    @Transactional
    public TransitionResponse transition(UUID id, TournamentStatus target) {
        Tournament tournament = require(id);
        TournamentStatus current = tournament.getStatus();

        if (!current.canTransitionTo(target)) {
            throw new ConflictException(
                "INVALID_STATE_TRANSITION",
                "Cannot transition tournament from " + current + " to " + target + "."
            );
        }

        // A tournament with nothing to enter cannot open for registration (BR-T-2).
        if (target == TournamentStatus.REGISTRATION_OPEN
            && !competitionRepository.existsByTournamentIdAndDeletedAtIsNull(id)) {
            throw new ConflictException(
                "NO_COMPETITIONS",
                "Add at least one competition before opening registration."
            );
        }

        // Completion means every competition has finished or been called off (BR-T-3).
        if (target == TournamentStatus.COMPLETED) {
            List<Competition> unfinished = competitionRepository
                .findByTournamentIdAndDeletedAtIsNullOrderByCreatedAtAsc(id)
                .stream()
                .filter(competition -> !competition.getStatus().isTerminal())
                .toList();
            if (!unfinished.isEmpty()) {
                throw new ConflictException(
                    "COMPETITIONS_NOT_FINISHED",
                    unfinished.size() + " competition(s) are neither COMPLETED nor CANCELLED."
                );
            }
        }

        if (target == TournamentStatus.PUBLISHED) {
            tournament.setPublishedAt(Instant.now());
        }

        // Cancelling the parent cancels everything still live beneath it (BR-T-4).
        if (target == TournamentStatus.CANCELLED) {
            competitionRepository.findByTournamentIdAndDeletedAtIsNullOrderByCreatedAtAsc(id).stream()
                .filter(competition -> !competition.getStatus().isTerminal())
                .forEach(competition -> competition.setStatus(CompetitionStatus.CANCELLED));
        }

        tournament.setStatus(target);
        return new TransitionResponse(tournament.getId(), target.name(), Instant.now());
    }

    public Tournament require(UUID id) {
        return tournamentRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("TOURNAMENT_NOT_FOUND", "Tournament not found."));
    }

    private void validateDates(java.time.LocalDate start, java.time.LocalDate end) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ValidationException("INVALID_DATE_RANGE", "endDate cannot be before startDate.");
        }
    }

    TournamentResponse toResponse(Tournament tournament) {
        return new TournamentResponse(
            tournament.getId(),
            tournament.getOrganizationUnitId(),
            tournament.getName(),
            tournament.getSlug(),
            tournament.getDescription(),
            tournament.getStatus(),
            tournament.getStartDate(),
            tournament.getEndDate(),
            tournament.getPublishedAt(),
            tournament.getCreatedAt()
        );
    }
}
