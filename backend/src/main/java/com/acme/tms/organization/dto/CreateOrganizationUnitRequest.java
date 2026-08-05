package com.acme.tms.organization.dto;

import com.acme.tms.organization.domain.OrganizationUnitType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateOrganizationUnitRequest(
    UUID parentOrganizationUnitId,
    @NotBlank @Size(max = 200) String name,
    @Pattern(regexp = "^[a-z0-9-]{3,120}$") String slug,
    @NotNull OrganizationUnitType type
) {
}

