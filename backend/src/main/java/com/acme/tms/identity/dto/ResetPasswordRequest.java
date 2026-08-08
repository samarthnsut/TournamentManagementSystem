package com.acme.tms.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Same 8-character floor as registration and the profile change — one policy, one place. */
public record ResetPasswordRequest(
    @NotBlank String token,
    @NotBlank @Size(min = 8, max = 120) String newPassword
) {
}
