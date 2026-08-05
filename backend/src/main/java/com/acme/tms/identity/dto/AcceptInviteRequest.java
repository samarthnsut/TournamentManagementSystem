package com.acme.tms.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInviteRequest(
    @NotBlank String inviteToken,
    @NotBlank @Size(min = 8, max = 120) String password,
    @Size(max = 200) String displayName
) {
}

