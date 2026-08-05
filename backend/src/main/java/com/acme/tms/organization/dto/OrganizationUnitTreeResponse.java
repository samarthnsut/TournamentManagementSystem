package com.acme.tms.organization.dto;

import com.acme.tms.organization.domain.OrganizationUnitStatus;
import com.acme.tms.organization.domain.OrganizationUnitType;

import java.util.List;
import java.util.UUID;

public record OrganizationUnitTreeResponse(
    UUID id,
    String name,
    String slug,
    OrganizationUnitType type,
    OrganizationUnitStatus status,
    List<OrganizationUnitTreeResponse> children
) {
}

