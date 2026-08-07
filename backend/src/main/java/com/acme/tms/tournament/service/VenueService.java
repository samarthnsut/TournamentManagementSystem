package com.acme.tms.tournament.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.tournament.domain.Venue;
import com.acme.tms.tournament.dto.VenueRequest;
import com.acme.tms.tournament.dto.VenueResponse;
import com.acme.tms.tournament.repository.VenueRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Grounds a match can be played at. Owned by an organization unit like everything else. */
@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public VenueService(
        VenueRepository venueRepository,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser
    ) {
        this.venueRepository = venueRepository;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    @Transactional
    @Audited(value = "venue:create", entityType = "Venue")
    public VenueResponse create(VenueRequest request) {
        Venue venue = new Venue();
        venue.setOrganizationUnitId(request.organizationUnitId());
        apply(venue, request);
        return toResponse(venueRepository.save(venue));
    }

    /**
     * Everything in the caller's subtree. Returns what they can see rather than refusing, matching
     * the organization-unit list and the approvals inbox.
     */
    @Transactional(readOnly = true)
    public List<VenueResponse> list() {
        List<UUID> visibleUnits =
            scopeEvaluator.visibleOrganizationUnitIds(currentUser.requireUserId(), "venue:read");

        if (visibleUnits.isEmpty()) {
            return List.of();
        }

        return venueRepository.findByOrganizationUnitIdInAndDeletedAtIsNullOrderByNameAsc(visibleUnits)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public VenueResponse get(UUID id) {
        return toResponse(require(id));
    }

    @Transactional
    @Audited(value = "venue:update", entityType = "Venue", entityIdParam = "id")
    public VenueResponse update(UUID id, VenueRequest request) {
        Venue venue = require(id);
        // The owning unit is deliberately not updatable: moving a venue between tenants would move
        // every match already scheduled at it.
        apply(venue, request);
        return toResponse(venue);
    }

    @Transactional
    @Audited(value = "venue:delete", entityType = "Venue", entityIdParam = "id")
    public void archive(UUID id) {
        require(id).markDeleted();
    }

    public Venue require(UUID id) {
        return venueRepository.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ResourceNotFoundException("VENUE_NOT_FOUND", "Venue not found."));
    }

    private void apply(Venue venue, VenueRequest request) {
        venue.setName(request.name().trim());
        venue.setAddressLine(request.addressLine());
        venue.setCity(request.city());
        venue.setState(request.state());
        venue.setCapacity(request.capacity());
    }

    private VenueResponse toResponse(Venue venue) {
        return new VenueResponse(
            venue.getId(),
            venue.getOrganizationUnitId(),
            venue.getName(),
            venue.getAddressLine(),
            venue.getCity(),
            venue.getState(),
            venue.getCapacity(),
            venue.getCreatedAt()
        );
    }
}
