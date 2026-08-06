package com.acme.tms.common.domain;

/**
 * What happens to a registration the moment it is submitted, when no ApprovalWorkflow applies.
 * Values are verbatim from 07_APPROVAL_WORKFLOW_ENGINE section 7.2.
 *
 * <p>Lives in {@code common} because {@code organization} and {@code tournament} both store it and
 * {@code registration} reads it.
 */
public enum RegistrationApprovalPolicy {

    /** Straight to APPROVED. Suits open club events where nobody is vetting entries. */
    AUTO_APPROVE,

    /** Stays PENDING until an organizer decides. The default, and the safer one. */
    DIRECT_SINGLE_APPROVAL
}
