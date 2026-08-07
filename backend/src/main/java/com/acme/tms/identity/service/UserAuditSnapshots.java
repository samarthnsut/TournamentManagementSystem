package com.acme.tms.identity.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.identity.domain.Role;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.domain.UserStatus;
import com.acme.tms.identity.repository.RoleRepository;
import com.acme.tms.identity.repository.UserRepository;
import com.acme.tms.identity.repository.UserRoleAssignmentRepository;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Audit snapshot for a User, carrying their role assignments — "who gave whom the ability to act"
 * is the question an auditor asks first, and a before/after diff of the grant list answers it.
 *
 * <p>Reads repositories directly rather than reusing {@code RoleAssignmentService.list}, for two
 * reasons that both bite:
 *
 * <ul>
 *   <li>That method filters by the caller's own permissions, so the same action recorded by two
 *       different actors would produce two different histories. A snapshot has to describe the row,
 *       not the reader.
 *   <li>It calls {@code currentUser.requireUserId()} and throws when there is no security context —
 *       during bootstrap registration, for one. Because it is {@code @Transactional}, that
 *       exception marks the caller's transaction rollback-only, and catching it in the aspect does
 *       not undo that. A provider that throws takes the business operation down with it.
 * </ul>
 *
 * <p>Which is the general rule for every provider: no caller context, no permission filtering, and
 * nothing that can throw.
 */
@Component
public class UserAuditSnapshots implements AuditSnapshotProvider {

    public static final String USER = "User";

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleRepository roleRepository;

    public UserAuditSnapshots(
        UserRepository userRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RoleRepository roleRepository
    ) {
        this.userRepository = userRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public String entityType() {
        return USER;
    }

    @Override
    public Optional<Object> snapshot(UUID entityId) {
        return userRepository.findById(entityId).map(this::toSnapshot);
    }

    private Object toSnapshot(User user) {
        List<GrantSnapshot> grants = userRoleAssignmentRepository
            .findByUserIdAndDeletedAtIsNull(user.getId())
            .stream()
            .map(assignment -> new GrantSnapshot(
                roleRepository.findById(assignment.getRoleId()).map(Role::getCode).orElse(null),
                assignment.getScopeType(),
                assignment.getScopeId()
            ))
            .toList();

        return new UserSnapshot(user.getId(), user.getEmail(), user.getFullName(), user.getStatus(), grants);
    }

    /** A record, not the entity: a detached entity blows up on serialization. */
    public record UserSnapshot(
        UUID id,
        String email,
        String fullName,
        UserStatus status,
        List<GrantSnapshot> roles
    ) {
    }

    public record GrantSnapshot(String roleCode, ScopeType scopeType, UUID scopeId) {
    }
}
