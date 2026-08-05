package com.acme.tms.access.dto;

import com.acme.tms.access.domain.ScopeType;

import java.util.UUID;

public record RoleAssignmentResponse(
    UUID id,
    UUID userId,
    String roleCode,
    ScopeType scopeType,
    UUID scopeId
) {
}
