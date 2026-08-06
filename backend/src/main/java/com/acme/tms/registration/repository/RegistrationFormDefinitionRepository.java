package com.acme.tms.registration.repository;

import com.acme.tms.registration.domain.RegistrationFormDefinition;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegistrationFormDefinitionRepository extends JpaRepository<RegistrationFormDefinition, UUID> {

    Optional<RegistrationFormDefinition> findByIdAndDeletedAtIsNull(UUID id);

    Optional<RegistrationFormDefinition> findByCompetitionIdAndIsActiveTrueAndDeletedAtIsNull(UUID competitionId);

    List<RegistrationFormDefinition> findByCompetitionIdAndDeletedAtIsNullOrderByVersionAsc(UUID competitionId);
}
