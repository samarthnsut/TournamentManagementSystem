package com.acme.tms.identity.service;

import java.util.UUID;

/**
 * Raised when a reset has been issued, consumed only after the transaction commits.
 *
 * <p>Carries the raw token because only its hash is stored. Same reasoning as
 * {@link UserInvitedEvent}: emailing before the commit could put a live-looking link in an inbox
 * for a transaction that then rolled back.
 */
public record PasswordResetRequestedEvent(
    UUID userId,
    String email,
    String fullName,
    String token,
    long validForMinutes
) {
}
