package com.acme.tms.identity.repository;

import com.acme.tms.common.security.ScopeType;
import com.acme.tms.identity.domain.Permission;
import com.acme.tms.identity.domain.UserRoleAssignment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRoleAssignmentRepository extends JpaRepository<UserRoleAssignment, UUID> {

    List<UserRoleAssignment> findByUserIdAndDeletedAtIsNull(UUID userId);

    Optional<UserRoleAssignment> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByUserIdAndRoleIdAndScopeTypeAndScopeIdAndDeletedAtIsNull(
        UUID userId,
        UUID roleId,
        ScopeType scopeType,
        UUID scopeId
    );

    boolean existsByUserIdAndRoleIdAndScopeTypeAndScopeIdIsNullAndDeletedAtIsNull(
        UUID userId,
        UUID roleId,
        ScopeType scopeType
    );

    long countByRoleIdAndScopeTypeAndDeletedAtIsNull(UUID roleId, ScopeType scopeType);

    /**
     * Permission codes the user holds at each of their assignment scopes. Resolved in one round trip
     * because this is the hottest read in the system (04_DATABASE_DESIGN §8.1, ix_ura_user).
     */
    @Query(value = """
        select ura.scope_type as "scopeType", ura.scope_id as "scopeId", p.code as "permissionCode"
        from user_role_assignment ura
        join role_permission rp on rp.role_id = ura.role_id
        join permission p on p.id = rp.permission_id and p.deleted_at is null
        where ura.user_id = :userId and ura.deleted_at is null
        """, nativeQuery = true)
    List<ScopedPermissionRow> findScopedPermissions(@Param("userId") UUID userId);

    interface ScopedPermissionRow {
        String getScopeType();

        UUID getScopeId();

        String getPermissionCode();
    }
}
