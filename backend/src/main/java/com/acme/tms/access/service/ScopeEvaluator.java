package com.acme.tms.access.service;

import com.acme.tms.access.domain.ScopeType;
import com.acme.tms.access.repository.UserRoleAssignmentRepository;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.organization.repository.OrganizationUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ScopeEvaluator {

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final OrganizationUnitRepository organizationUnitRepository;

    public ScopeEvaluator(
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        OrganizationUnitRepository organizationUnitRepository
    ) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.organizationUnitRepository = organizationUnitRepository;
    }

    /**
     * True when the user holds {@code permissionCode} at a scope covering {@code target}. A GLOBAL
     * grant covers everything; an ORGANIZATION grant covers its whole subtree (BR-URA-3).
     */
    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String permissionCode, ScopeTarget target) {
        List<UserRoleAssignmentRepository.ScopedPermissionRow> rows =
            userRoleAssignmentRepository.findScopedPermissions(userId);

        List<UUID> organizationRoots = new ArrayList<>();
        Set<UUID> directScopeIds = new HashSet<>();

        for (UserRoleAssignmentRepository.ScopedPermissionRow row : rows) {
            if (!row.getPermissionCode().equals(permissionCode)) {
                continue;
            }

            ScopeType grantedScope = ScopeType.valueOf(row.getScopeType());
            if (grantedScope == ScopeType.GLOBAL) {
                return true;
            }
            if (grantedScope == ScopeType.ORGANIZATION) {
                organizationRoots.add(row.getScopeId());
            }
            if (grantedScope == target.scopeType()) {
                directScopeIds.add(row.getScopeId());
            }
        }

        if (target.scopeType() == ScopeType.GLOBAL) {
            return false;
        }
        if (directScopeIds.contains(target.scopeId())) {
            return true;
        }
        if (target.scopeType() != ScopeType.ORGANIZATION) {
            // An ORGANIZATION grant should also cover tournaments and competitions owned inside its
            // subtree, which needs the owning-unit lookup that arrives with Tournament in Sprint 3.
            throw new ValidationException(
                "SCOPE_TYPE_NOT_SUPPORTED",
                "Scope " + target.scopeType() + " cannot be resolved until tournaments exist."
            );
        }
        if (organizationRoots.isEmpty()) {
            return false;
        }

        return organizationUnitRepository.findSubtreeIds(organizationRoots).contains(target.scopeId());
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
}
