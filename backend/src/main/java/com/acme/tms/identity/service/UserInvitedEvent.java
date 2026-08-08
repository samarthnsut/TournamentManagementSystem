package com.acme.tms.identity.service;

import java.util.UUID;

/**
 * Raised when an invite has been created, consumed only after the transaction commits.
 *
 * <p>Carries the raw {@code inviteToken} because only its SHA-256 hash is stored — this is the one
 * moment the token exists in readable form. The event never leaves the process, so it is not
 * written anywhere; a listener that wanted to persist it would be storing a live credential.
 */
public record UserInvitedEvent(
    UUID userId,
    String email,
    String fullName,
    String inviteToken,
    String invitedByName
) {
}
