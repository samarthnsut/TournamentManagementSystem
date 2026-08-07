package com.acme.tms.registration.service;

import com.acme.tms.common.document.AttachableEntityResolver;
import com.acme.tms.registration.repository.RegistrationRepository;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/** Age proofs, medical certificates and consent forms hang off a registration. */
@Component
public class RegistrationAttachable implements AttachableEntityResolver {

    public static final String REGISTRATION = "REGISTRATION";

    private final RegistrationRepository registrationRepository;

    public RegistrationAttachable(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    @Override
    public String entityType() {
        return REGISTRATION;
    }

    @Override
    public Optional<UUID> organizationUnitOf(UUID entityId) {
        return registrationRepository.findByIdAndDeletedAtIsNull(entityId)
            .map(registration -> registration.getOrganizationUnitId());
    }
}
