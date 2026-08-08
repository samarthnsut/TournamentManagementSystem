package com.acme.tms.identity.dto;

import com.acme.tms.identity.domain.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The signed-in user's own record.
 *
 * <p>Richer than {@link UserResponse} because this is the one place someone looks at themselves:
 * it carries the roles they hold and where, which is how they find out why they cannot do
 * something.
 */
public record ProfileResponse(
    UUID id,
    String email,
    String fullName,
    String phone,
    UserStatus status,
    Instant createdAt,
    List<RoleAssignmentResponse> roles,
    List<String> permissions
) {
}
