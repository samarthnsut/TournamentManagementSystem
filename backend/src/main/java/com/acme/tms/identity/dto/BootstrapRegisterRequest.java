package com.acme.tms.identity.dto;

import com.acme.tms.organization.domain.OrganizationUnitType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BootstrapRegisterRequest(
    @NotBlank @Size(max = 200) String fullName,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(min = 8, max = 120) String password,
    @NotBlank @Size(max = 200) String organizationName,
    OrganizationUnitType organizationType
) {
}

