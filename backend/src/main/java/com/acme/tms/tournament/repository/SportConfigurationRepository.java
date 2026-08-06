package com.acme.tms.tournament.repository;

import com.acme.tms.tournament.domain.SportConfiguration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SportConfigurationRepository extends JpaRepository<SportConfiguration, UUID> {

    Optional<SportConfiguration> findByIdAndDeletedAtIsNull(UUID id);

    List<SportConfiguration> findByOrganizationUnitIdInAndDeletedAtIsNullOrderByCreatedAtDesc(
        Collection<UUID> organizationUnitIds);

    List<SportConfiguration> findByOrganizationUnitIdInAndSportIdAndDeletedAtIsNullOrderByCreatedAtDesc(
        Collection<UUID> organizationUnitIds, UUID sportId);
}
