package com.acme.tms.tournament.service;

import com.acme.tms.common.security.ScopeOwnershipResolver;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.tournament.repository.VenueRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * A venue is covered by a grant on the organization unit that owns it, or on any unit above it.
 * Same shape as {@code MatchScopeResolver} and for the same reason (ADR-015): the endpoint is
 * addressed by venue id, but the authority comes from the tenant.
 */
@Component
public class VenueScopeResolver implements ScopeOwnershipResolver {

    private final VenueRepository venueRepository;

    public VenueScopeResolver(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Override
    public ScopeType scopeType() {
        return ScopeType.VENUE;
    }

    @Override
    public Optional<ResolvedScope> resolve(UUID scopeId) {
        return venueRepository.findByIdAndDeletedAtIsNull(scopeId)
            .map(venue -> ResolvedScope.ownedBy(venue.getOrganizationUnitId()));
    }
}
