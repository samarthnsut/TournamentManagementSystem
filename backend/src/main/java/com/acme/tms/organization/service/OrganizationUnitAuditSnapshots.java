package com.acme.tms.organization.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.common.exception.ResourceNotFoundException;

import com.acme.tms.organization.repository.OrganizationUnitRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Audit snapshot for an OrganizationUnit — the tenant tree is the most sensitive thing to change. */
/*
 * Providers must not throw. A ResourceNotFoundException raised inside a nested @Transactional
 * service method marks the caller's transaction rollback-only *before* the audit aspect can catch
 * it, which would take down the very operation being recorded. Existence is therefore checked
 * against the repository first, and the throwing read is only reached when it cannot throw.
 */
@Component
public class OrganizationUnitAuditSnapshots implements AuditSnapshotProvider {

    public static final String ORGANIZATION_UNIT = "OrganizationUnit";

    private final OrganizationUnitService organizationUnitService;
    private final OrganizationUnitRepository organizationUnitRepository;

    public OrganizationUnitAuditSnapshots(OrganizationUnitService organizationUnitService, OrganizationUnitRepository organizationUnitRepository) {
        this.organizationUnitService = organizationUnitService;
        this.organizationUnitRepository = organizationUnitRepository;
    }

    @Override
    public String entityType() {
        return ORGANIZATION_UNIT;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Object> snapshot(UUID entityId) {
        if (organizationUnitRepository.findByIdAndDeletedAtIsNull(entityId).isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(organizationUnitService.get(entityId));
        } catch (ResourceNotFoundException exception) {
            return Optional.empty();
        }
    }
}
