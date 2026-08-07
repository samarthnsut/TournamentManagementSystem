package com.acme.tms.tournament.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.tournament.domain.Venue;
import com.acme.tms.tournament.repository.VenueRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Audit snapshot for a Venue. Reads the repository directly and maps here rather than calling
 * {@code VenueService.get}, which throws when the row is missing — and an exception from a nested
 * transactional call would mark the caller's transaction rollback-only (ADR-017).
 */
@Component
public class VenueAuditSnapshots implements AuditSnapshotProvider {

    public static final String VENUE = "Venue";

    private final VenueRepository venueRepository;

    public VenueAuditSnapshots(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    @Override
    public String entityType() {
        return VENUE;
    }

    @Override
    public Optional<Object> snapshot(UUID entityId) {
        return venueRepository.findByIdAndDeletedAtIsNull(entityId).map(this::toSnapshot);
    }

    private Object toSnapshot(Venue venue) {
        return new Snapshot(
            venue.getOrganizationUnitId(),
            venue.getName(),
            venue.getAddressLine(),
            venue.getCity(),
            venue.getState(),
            venue.getCapacity());
    }

    public record Snapshot(
        UUID organizationUnitId,
        String name,
        String addressLine,
        String city,
        String state,
        Integer capacity
    ) {
    }
}
