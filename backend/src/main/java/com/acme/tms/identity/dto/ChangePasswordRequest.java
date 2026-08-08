package com.acme.tms.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param currentPassword proves the person at the keyboard is the account holder, not someone who
 *     found an unlocked laptop
 * @param newPassword same floor as registration (8 chars); one policy, one place
 */
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8, max = 120) String newPassword
) {
}
