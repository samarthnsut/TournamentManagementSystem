package com.acme.tms.identity.dto;

import com.acme.tms.identity.domain.UserStatus;

import java.util.UUID;

public record InviteUserResponse(
    UUID id,
    String email,
    String displayName,
    UserStatus status,
    UUID organizationUnitId,
    String inviteToken
) {
}

