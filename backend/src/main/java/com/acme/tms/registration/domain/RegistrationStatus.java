package com.acme.tms.registration.domain;

/**
 * Deliberately flat. Multi-level approval progress lives in the workflow engine (Sprint 5), never
 * encoded here as extra states — BR-REG-5.
 */
public enum RegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED,
    WITHDRAWN;

    /** Once decided or withdrawn, a registration no longer accepts changes. */
    public boolean isFinal() {
        return this != PENDING;
    }
}
