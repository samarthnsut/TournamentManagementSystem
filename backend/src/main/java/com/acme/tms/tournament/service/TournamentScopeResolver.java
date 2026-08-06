package com.acme.tms.tournament.service;

import com.acme.tms.common.security.ScopeOwnershipResolver;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.tournament.domain.Tournament;
import com.acme.tms.tournament.repository.TournamentRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** A tournament is covered by an ORGANIZATION grant on the unit that owns it, or any ancestor. */
@Component
public class TournamentScopeResolver implements ScopeOwnershipResolver {

    private final TournamentRepository tournamentRepository;

    public TournamentScopeResolver(TournamentRepository tournamentRepository) {
        this.tournamentRepository = tournamentRepository;
    }

    @Override
    public ScopeType scopeType() {
        return ScopeType.TOURNAMENT;
    }

    @Override
    public Optional<ResolvedScope> resolve(UUID scopeId) {
        return tournamentRepository.findByIdAndDeletedAtIsNull(scopeId)
            .map(Tournament::getOrganizationUnitId)
            .map(ResolvedScope::ownedBy);
    }
}
