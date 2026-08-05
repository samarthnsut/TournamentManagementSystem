package com.acme.tms.identity.dto;

import com.acme.tms.common.security.ScopeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRoleAssignmentRequest(
    @NotBlank String roleCode,
    @NotNull ScopeType scopeType,
    UUID scopeId
) {
}
