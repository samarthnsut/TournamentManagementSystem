package com.acme.tms.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Email is deliberately absent. Changing it is an identity change and needs a verification round
 * trip to the new address — without one, a typo locks you out of your own account. Tracked as a
 * gap rather than silently allowed.
 */
public record UpdateProfileRequest(
    @NotBlank @Size(max = 200) String fullName,
    @Size(max = 20) @Pattern(
        regexp = "^$|^[+0-9 ()-]{6,20}$",
        message = "must be a plausible phone number"
    ) String phone
) {
}
