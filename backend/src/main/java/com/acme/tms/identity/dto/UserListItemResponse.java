package com.acme.tms.identity.dto;

import com.acme.tms.identity.domain.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A user as a directory screen shows them: who they are, and what they can do where. */
public record UserListItemResponse(
    UUID id,
    String email,
    String displayName,
    UserStatus status,
    Instant createdAt,
    List<RoleAssignmentResponse> roles
) {
}
