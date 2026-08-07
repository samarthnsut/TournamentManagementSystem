package com.acme.tms.tournament.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.common.exception.ResourceNotFoundException;

import com.acme.tms.tournament.repository.SportConfigurationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Changing a sport configuration changes how every competition using it is scored. */
/*
 * Providers must not throw. A ResourceNotFoundException raised inside a nested @Transactional
 * service method marks the caller's transaction rollback-only *before* the audit aspect can catch
 * it, which would take down the very operation being recorded. Existence is therefore checked
 * against the repository first, and the throwing read is only reached when it cannot throw.
 */
@Component
public class SportConfigurationAuditSnapshots implements AuditSnapshotProvider {

    public static final String SPORT_CONFIGURATION = "SportConfiguration";

    private final SportConfigurationService sportConfigurationService;
    private final SportConfigurationRepository sportConfigurationRepository;

    public SportConfigurationAuditSnapshots(SportConfigurationService sportConfigurationService, SportConfigurationRepository sportConfigurationRepository) {
        this.sportConfigurationService = sportConfigurationService;
        this.sportConfigurationRepository = sportConfigurationRepository;
    }

    @Override
    public String entityType() {
        return SPORT_CONFIGURATION;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Object> snapshot(UUID entityId) {
        if (sportConfigurationRepository.findByIdAndDeletedAtIsNull(entityId).isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(sportConfigurationService.get(entityId));
        } catch (ResourceNotFoundException exception) {
            return Optional.empty();
        }
    }
}
