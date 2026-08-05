package com.acme.tms.identity.dto;

import com.acme.tms.common.security.ScopeType;

import java.util.UUID;

public record RoleAssignmentResponse(
    UUID id,
    UUID userId,
    String roleCode,
    ScopeType scopeType,
    UUID scopeId
) {
}
