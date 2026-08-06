package com.acme.tms.registration.repository;

import com.acme.tms.registration.domain.RegistrationResponse;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RegistrationResponseRepository extends JpaRepository<RegistrationResponse, UUID> {

    Optional<RegistrationResponse> findByRegistrationId(UUID registrationId);

    /** Drives the "this version has been answered, so it is frozen" rule (BR-RFD-2). */
    boolean existsByFormDefinitionId(UUID formDefinitionId);
}
