package com.acme.tms.access.dto;

import com.acme.tms.access.domain.ScopeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateRoleAssignmentRequest(
    @NotBlank String roleCode,
    @NotNull ScopeType scopeType,
    UUID scopeId
) {
}
