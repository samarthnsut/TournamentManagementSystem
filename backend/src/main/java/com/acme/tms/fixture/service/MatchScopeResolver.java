package com.acme.tms.fixture.service;

import com.acme.tms.common.security.ScopeOwnershipResolver;
import com.acme.tms.common.security.ScopeTarget;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.fixture.repository.MatchRepository;
import com.acme.tms.tournament.repository.CompetitionRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Match endpoints are addressed by match id, but nobody is ever granted a role "on a match" — the
 * authority comes from the competition it belongs to, and from everything above that.
 *
 * <p>MATCH therefore exists as a scope target only. It is absent from the roles table's scope
 * check constraint on purpose: resolving through here is the single path, so a grant at match level
 * cannot be created and then quietly relied on.
 */
@Component
public class MatchScopeResolver implements ScopeOwnershipResolver {

    private final MatchRepository matchRepository;
    private final CompetitionRepository competitionRepository;

    public MatchScopeResolver(MatchRepository matchRepository, CompetitionRepository competitionRepository) {
        this.matchRepository = matchRepository;
        this.competitionRepository = competitionRepository;
    }

    @Override
    public ScopeType scopeType() {
        return ScopeType.MATCH;
    }

    @Override
    public Optional<ResolvedScope> resolve(UUID scopeId) {
        return matchRepository.findById(scopeId)
            .flatMap(match -> competitionRepository.findByIdAndDeletedAtIsNull(match.getCompetitionId()))
            .map(competition -> new ResolvedScope(
                competition.getOrganizationUnitId(),
                List.of(
                    new ScopeTarget(ScopeType.COMPETITION, competition.getId()),
                    new ScopeTarget(ScopeType.TOURNAMENT, competition.getTournamentId())
                )
            ));
    }
}
