package com.acme.tms.common.audit;

import java.util.Optional;
import java.util.UUID;

/**
 * Tells the audit aspect what an entity looked like at a moment in time.
 *
 * <p>A port, not a helper, for the same reason as {@code ScopeOwnershipResolver}: the audit trail
 * lives in its own module, but only the owning module knows how to load a Tournament and which of
 * its fields are worth keeping. Each module implements this for the entities it owns, so the audit
 * code never depends on a domain package.
 *
 * <p>Implementations must return a **DTO-level snapshot**, never a JPA entity (ADR-014): a detached
 * entity serialized outside its session throws on the first lazy association, and the failure would
 * surface as a lost audit row rather than as an obvious bug.
 */
public interface AuditSnapshotProvider {

    /** The entity type this provider answers for, matching {@code Audited.entityType()}. */
    String entityType();

    /**
     * @return the current state, or empty when the entity does not exist — which is the normal case
     *     for the "before" side of a create, and for the "after" side of a hard delete
     */
    Optional<Object> snapshot(UUID entityId);
}
