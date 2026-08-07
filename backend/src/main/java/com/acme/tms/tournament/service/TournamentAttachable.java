package com.acme.tms.tournament.service;

import com.acme.tms.common.document.AttachableEntityResolver;
import com.acme.tms.tournament.repository.TournamentRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Rulebooks, schedules and sanction letters hang off a tournament. */
@Component
public class TournamentAttachable implements AttachableEntityResolver {

    public static final String TOURNAMENT = "TOURNAMENT";

    private final TournamentRepository tournamentRepository;

    public TournamentAttachable(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public String entityType() {
        return TOURNAMENT;
    }

    @Override
    public Optional<UUID> organizationUnitOf(UUID entityId) {
        return tournamentRepository.findByIdAndDeletedAtIsNull(entityId)
            .map(tournament -> tournament.getOrganizationUnitId());
    }
}
