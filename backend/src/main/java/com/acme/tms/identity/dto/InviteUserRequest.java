package com.acme.tms.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InviteUserRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 200) String displayName,
    @NotNull UUID organizationUnitId,
    @Valid CreateRoleAssignmentRequest initialRole
) {
}
