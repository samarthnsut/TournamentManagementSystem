package com.acme.tms.organization.dto;

import com.acme.tms.organization.domain.OrganizationUnitStatus;
import com.acme.tms.organization.domain.OrganizationUnitType;

import java.time.Instant;
import java.util.UUID;

public record OrganizationUnitResponse(
    UUID id,
    UUID parentOrganizationUnitId,
    String name,
    String slug,
    OrganizationUnitType type,
    OrganizationUnitStatus status,
    Instant createdAt
) {
}

