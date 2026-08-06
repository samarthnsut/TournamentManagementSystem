package com.acme.tms.common.security;

import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.identity.repository.UserRoleAssignmentRepository;
import com.acme.tms.organization.repository.OrganizationUnitRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ScopeEvaluator {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final Map<ScopeType, ScopeOwnershipResolver> ownershipResolvers;

    public ScopeEvaluator(
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository,
        List<ScopeOwnershipResolver> ownershipResolvers
    ) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;

        Map<ScopeType, ScopeOwnershipResolver> byType = new EnumMap<>(ScopeType.class);
        for (ScopeOwnershipResolver resolver : ownershipResolvers) {
            ScopeOwnershipResolver clash = byType.put(resolver.scopeType(), resolver);
            if (clash != null) {
                throw new IllegalStateException(
                    "Duplicate ScopeOwnershipResolver for " + resolver.scopeType() + ": "
                        + clash.getClass().getName() + " and " + resolver.getClass().getName()
                );
            }
        }
        this.ownershipResolvers = Map.copyOf(byType);
    }

    /**
     * True when the user holds {@code permissionCode} at a scope covering {@code target}.
     *
     * <p>Access widens outwards: an exact grant on the entity, a grant on a coarser scope that
     * contains it (a tournament covers its competitions), or an ORGANIZATION grant anywhere above
     * the owning unit, since that covers the whole subtree (BR-URA-3). GLOBAL covers everything.
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String permissionCode, ScopeTarget target) {
        Map<ScopeType, Set<UUID>> grants = new EnumMap<>(ScopeType.class);

        for (UserRoleAssignmentRepository.ScopedPermissionRow row :
            userRoleAssignmentRepository.findScopedPermissions(userId)) {
            if (!row.getPermissionCode().equals(permissionCode)) {
                continue;
            }

            ScopeType grantedScope = ScopeType.valueOf(row.getScopeType());
            if (grantedScope == ScopeType.GLOBAL) {
                return true;
            }
            grants.computeIfAbsent(grantedScope, key -> new HashSet<>()).add(row.getScopeId());
        }

        // Only a GLOBAL grant satisfies a GLOBAL requirement, and that already returned above.
        if (target.scopeType() == ScopeType.GLOBAL) {
            return false;
        }

        if (grants.getOrDefault(target.scopeType(), Set.of()).contains(target.scopeId())) {
            return true;
        }

        Set<UUID> organizationRoots = grants.getOrDefault(ScopeType.ORGANIZATION, Set.of());

        if (target.scopeType() == ScopeType.ORGANIZATION) {
            return coveredByOrganizationGrant(organizationRoots, target.scopeId());
        }

        ScopeOwnershipResolver resolver = ownershipResolvers.get(target.scopeType());
        if (resolver == null) {
            // A scope type with no resolver deployed cannot be evaluated; failing loudly beats
            // silently denying, which would look like a permissions bug.
            throw new ValidationException(
                "SCOPE_TYPE_NOT_SUPPORTED",
                "No ownership resolver is deployed for scope " + target.scopeType() + "."
            );
        }

        Optional<ScopeOwnershipResolver.ResolvedScope> resolved = resolver.resolve(target.scopeId());
        if (resolved.isEmpty()) {
            return false;
        }

        for (ScopeTarget parent : resolved.get().parentScopes()) {
            if (grants.getOrDefault(parent.scopeType(), Set.of()).contains(parent.scopeId())) {
                return true;
            }
        }

        return coveredByOrganizationGrant(organizationRoots, resolved.get().organizationUnitId());
    }

    /**
     * Every organization unit the user can reach with {@code permissionCode}. Empty means "no
     * organization-scoped access"; a GLOBAL holder gets every unit.
     */
    @Transactional(readOnly = true)
    public List<UUID> visibleOrganizationUnitIds(UUID userId, String permissionCode) {
        List<UUID> organizationRoots = new ArrayList<>();

        for (UserRoleAssignmentRepository.ScopedPermissionRow row : userRoleAssignmentRepository.findScopedPermissions(userId)) {
            if (!row.getPermissionCode().equals(permissionCode)) {
                continue;
            }

            ScopeType grantedScope = ScopeType.valueOf(row.getScopeType());
            if (grantedScope == ScopeType.GLOBAL) {
                return organizationUnitRepository.findByDeletedAtIsNullOrderByCreatedAtAsc()
                    .stream()
                    .map(unit -> unit.getId())
                    .toList();
            }
            if (grantedScope == ScopeType.ORGANIZATION) {
                organizationRoots.add(row.getScopeId());
            }
        }

        return organizationRoots.isEmpty() ? List.of() : organizationUnitRepository.findSubtreeIds(organizationRoots);
    }

    private boolean coveredByOrganizationGrant(Set<UUID> organizationRoots, UUID organizationUnitId) {
        if (organizationRoots.isEmpty() || organizationUnitId == null) {
            return false;
        }
        if (organizationRoots.contains(organizationUnitId)) {
            return true;
        }
        return organizationUnitRepository.findSubtreeIds(List.copyOf(organizationRoots)).contains(organizationUnitId);
    }
}
