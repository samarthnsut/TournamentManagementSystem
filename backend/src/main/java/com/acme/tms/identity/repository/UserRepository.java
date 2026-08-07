package com.acme.tms.identity.repository;

import com.acme.tms.identity.domain.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<User> findByInviteTokenHashAndDeletedAtIsNull(String inviteTokenHash);

    /**
     * Users holding at least one ORGANIZATION-scoped assignment inside the given units.
     *
     * <p>Membership is expressed only through role assignments — there is no
     * {@code user.organization_unit_id} — so this join *is* the tenant boundary for a user listing.
     * A user with roles in two subtrees legitimately appears in both, which is the model working
     * rather than a leak.
     */
    @Query("select distinct u from User u where u.deletedAt is null and exists ("
        + "select 1 from UserRoleAssignment a where a.userId = u.id and a.deletedAt is null "
        + "and a.scopeType = com.acme.tms.common.security.ScopeType.ORGANIZATION "
        + "and a.scopeId in :organizationUnitIds) order by u.fullName asc")
    List<User> findInOrganizationUnits(@Param("organizationUnitIds") Collection<UUID> organizationUnitIds);
}
