package com.acme.tms.tournament.repository;

import com.acme.tms.tournament.domain.Venue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    Optional<Venue> findByIdAndDeletedAtIsNull(UUID id);

    /** Scoped listing: the caller's reachable units, never the whole table. */
    List<Venue> findByOrganizationUnitIdInAndDeletedAtIsNullOrderByNameAsc(
        Collection<UUID> organizationUnitIds);
}
