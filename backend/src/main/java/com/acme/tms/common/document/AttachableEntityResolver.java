package com.acme.tms.common.document;

import java.util.Optional;
import java.util.UUID;

/**
 * Tells the document module what a file may be attached to, and who owns it.
 *
 * <p>A port for the same reason as {@code ScopeOwnershipResolver} and {@code AuditSnapshotProvider}:
 * documents are polymorphic ({@code entityType} + {@code entityId}), but only the owning module
 * knows how to load a Registration and which organization unit it belongs to.
 *
 * <p>It doubles as the allow-list of attachable types. A caller can only name an {@code entityType}
 * that some module has deployed a resolver for, so "attach this to an audit_log row" is not a thing
 * that can be asked for — and the owning unit it returns is what the permission check is run
 * against, so a file can never be attached across a tenant boundary.
 */
public interface AttachableEntityResolver {

    /** The {@code entityType} token clients use, e.g. {@code REGISTRATION}. */
    String entityType();

    /** Empty when no such entity exists, which denies the upload rather than erroring. */
    Optional<UUID> organizationUnitOf(UUID entityId);
}
