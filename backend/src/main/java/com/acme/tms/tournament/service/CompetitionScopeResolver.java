package com.acme.tms.tournament.service;

import com.acme.tms.common.security.ScopeOwnershipResolver;
import com.acme.tms.common.security.ScopeTarget;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.tournament.repository.CompetitionRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A competition is covered by a grant on itself, on its tournament, or on any organization unit at
 * or above its owner — which is how a TOURNAMENT_ADMIN reaches every competition they run.
 */
@Component
public class CompetitionScopeResolver implements ScopeOwnershipResolver {

    private final CompetitionRepository competitionRepository;

    public CompetitionScopeResolver(CompetitionRepository competitionRepository) {
        this.competitionRepository = competitionRepository;
    }

    @Override
    public ScopeType scopeType() {
        return ScopeType.COMPETITION;
    }

    @Override
    public Optional<ResolvedScope> resolve(UUID scopeId) {
        return competitionRepository.findByIdAndDeletedAtIsNull(scopeId)
            .map(competition -> new ResolvedScope(
                competition.getOrganizationUnitId(),
                List.of(new ScopeTarget(ScopeType.TOURNAMENT, competition.getTournamentId()))
            ));
    }
}
