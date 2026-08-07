package com.acme.tms.tournament.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.common.exception.ResourceNotFoundException;

import com.acme.tms.tournament.repository.CompetitionRepository;
import com.acme.tms.tournament.repository.TournamentRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Audit snapshots for the tournament module's entities.
 *
 * <p>Each provider hands back the module's own read DTO. Reusing it means the audit trail records
 * exactly what the API would have shown at that moment, and that a field added to the response is
 * captured without anyone updating an audit-specific mapper.
 *
 * <p>A missing entity is empty, not an exception: that is the normal "before" state of a create.
 */
public final class TournamentAuditSnapshots {

    private TournamentAuditSnapshots() {
    }

    public static final String TOURNAMENT = "Tournament";
    public static final String COMPETITION = "Competition";

    @Component
    public static class TournamentSnapshots implements AuditSnapshotProvider {

        private final TournamentService tournamentService;
        private final TournamentRepository tournamentRepository;

        public TournamentSnapshots(
            TournamentService tournamentService,
            TournamentRepository tournamentRepository
        ) {
            this.tournamentService = tournamentService;
            this.tournamentRepository = tournamentRepository;
        }

        @Override
        public String entityType() {
            return TOURNAMENT;
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<Object> snapshot(UUID entityId) {
            if (tournamentRepository.findByIdAndDeletedAtIsNull(entityId).isEmpty()) {
                return Optional.empty();
            }

            try {
                return Optional.of(tournamentService.get(entityId));
            } catch (ResourceNotFoundException exception) {
                return Optional.empty();
            }
        }
    }

    @Component
    public static class CompetitionSnapshots implements AuditSnapshotProvider {

        private final CompetitionService competitionService;
        private final CompetitionRepository competitionRepository;

        public CompetitionSnapshots(
            CompetitionService competitionService,
            CompetitionRepository competitionRepository
        ) {
            this.competitionService = competitionService;
            this.competitionRepository = competitionRepository;
        }

        @Override
        public String entityType() {
            return COMPETITION;
        }

        @Override
        @Transactional(readOnly = true)
        public Optional<Object> snapshot(UUID entityId) {
            if (competitionRepository.findByIdAndDeletedAtIsNull(entityId).isEmpty()) {
                return Optional.empty();
            }

            try {
                return Optional.of(competitionService.get(entityId));
            } catch (ResourceNotFoundException exception) {
                return Optional.empty();
            }
        }
    }
}
