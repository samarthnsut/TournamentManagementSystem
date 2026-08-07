package com.acme.tms.tournament.repository;

import com.acme.tms.tournament.domain.Venue;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VenueRepository extends JpaRepository<Venue, UUID> {

    Optional<Venue> findByIdAndDeletedAtIsNull(UUID id);
}
