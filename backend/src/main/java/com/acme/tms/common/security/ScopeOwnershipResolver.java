package com.acme.tms.common.security;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tells {@link ScopeEvaluator} who owns a scoped entity.
 *
 * <p>This is a port, not a helper: authorization lives in {@code common}, but only the owning
 * module knows that a competition belongs to a tournament which belongs to an organization unit.
 * Each module implements this for its own scope type, so {@code common} never has to depend on
 * {@code tournament} — the layering rule in 13_CODING_STANDARDS section 1.
 */
public interface ScopeOwnershipResolver {

    /** The scope type this resolver can answer for. One resolver per type. */
    ScopeType scopeType();

    /** Empty when no such entity exists, which denies access rather than erroring. */
    Optional<ResolvedScope> resolve(UUID scopeId);

    /**
     * @param organizationUnitId the unit that owns the entity, so an ORGANIZATION grant anywhere
     *     above it in the tree confers access
     * @param parentScopes coarser scopes that also cover this entity — a COMPETITION is covered by
     *     a grant on its TOURNAMENT
     */
    record ResolvedScope(UUID organizationUnitId, List<ScopeTarget> parentScopes) {

        public static ResolvedScope ownedBy(UUID organizationUnitId) {
            return new ResolvedScope(organizationUnitId, List.of());
        }
    }
}
