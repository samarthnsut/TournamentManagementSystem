package com.acme.tms.tournament.repository;

import com.acme.tms.tournament.domain.Tournament;
import com.acme.tms.tournament.domain.TournamentStatus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TournamentRepository extends JpaRepository<Tournament, UUID> {

    Optional<Tournament> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Tournament> findBySlugAndDeletedAtIsNull(String slug);

    boolean existsBySlugAndDeletedAtIsNull(String slug);

    List<Tournament> findByOrganizationUnitIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
        Collection<UUID> organizationUnitIds);

    List<Tournament> findByOrganizationUnitIdInAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
        Collection<UUID> organizationUnitIds, TournamentStatus status);
}
