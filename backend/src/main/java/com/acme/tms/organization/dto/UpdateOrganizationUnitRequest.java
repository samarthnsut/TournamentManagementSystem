package com.acme.tms.organization.dto;

import com.acme.tms.organization.domain.OrganizationUnitStatus;

import jakarta.validation.constraints.Size;

public record UpdateOrganizationUnitRequest(
    @Size(max = 200) String name,
    OrganizationUnitStatus status
) {
}

