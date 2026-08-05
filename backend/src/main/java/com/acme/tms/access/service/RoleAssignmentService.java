package com.acme.tms.access.service;

import com.acme.tms.access.domain.Role;
import com.acme.tms.access.domain.ScopeType;
import com.acme.tms.access.domain.UserRoleAssignment;
import com.acme.tms.access.dto.CreateRoleAssignmentRequest;
import com.acme.tms.access.dto.RoleAssignmentResponse;
import com.acme.tms.access.repository.RoleRepository;
import com.acme.tms.access.repository.UserRoleAssignmentRepository;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ScopeAccessDeniedException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.identity.repository.UserRepository;
import com.acme.tms.organization.repository.OrganizationUnitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoleAssignmentService {

    private static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final OrganizationUnitRepository organizationUnitRepository;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public RoleAssignmentService(
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RoleRepository roleRepository,
        UserRepository userRepository,
        OrganizationUnitRepository organizationUnitRepository,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser
    ) {
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.organizationUnitRepository = organizationUnitRepository;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    @Transactional
    public RoleAssignmentResponse assign(UUID userId, CreateRoleAssignmentRequest request) {
        requireGrantAuthority(request.scopeType(), request.scopeId());
        return grant(userId, request.roleCode(), request.scopeType(), request.scopeId());
    }

    /**
     * Grants without an authority check — for flows where the caller has already been authorized, or
     * has no caller at all (tenant bootstrap).
     */
    @Transactional
    public RoleAssignmentResponse grant(UUID userId, String roleCode, ScopeType scopeType, UUID scopeId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found.");
        }

        Role role = roleRepository.findByCodeAndDeletedAtIsNull(roleCode)
            .orElseThrow(() -> new ResourceNotFoundException("ROLE_NOT_FOUND", "Role not found: " + roleCode));

        validateScope(role, scopeType, scopeId);

        boolean exists = scopeId == null
            ? userRoleAssignmentRepository.existsByUserIdAndRoleIdAndScopeTypeAndScopeIdIsNullAndDeletedAtIsNull(userId, role.getId(), scopeType)
            : userRoleAssignmentRepository.existsByUserIdAndRoleIdAndScopeTypeAndScopeIdAndDeletedAtIsNull(userId, role.getId(), scopeType, scopeId);
        if (exists) {
            throw new ConflictException("ASSIGNMENT_EXISTS", "This role is already assigned at that scope.");
        }

        UserRoleAssignment assignment = new UserRoleAssignment();
        assignment.setUserId(userId);
        assignment.setRoleId(role.getId());
        assignment.setScopeType(scopeType);
        assignment.setScopeId(scopeId);

        return toResponse(userRoleAssignmentRepository.save(assignment), role.getCode());
    }

    /**
     * Own assignments are always readable; another user's are filtered to the grants the caller can
     * actually see, so listing never becomes a way to enumerate other tenants.
     */
    @Transactional(readOnly = true)
    public List<RoleAssignmentResponse> list(UUID userId) {
        UUID callerId = currentUser.requireUserId();

        return userRoleAssignmentRepository.findByUserIdAndDeletedAtIsNull(userId)
            .stream()
            .filter(assignment -> callerId.equals(userId) || scopeEvaluator.hasPermission(
                callerId,
                "role:read",
                new ScopeTarget(assignment.getScopeType(), assignment.getScopeId())
            ))
            .map(assignment -> toResponse(assignment, roleCode(assignment.getRoleId())))
            .toList();
    }

    @Transactional
    public void revoke(UUID userId, UUID assignmentId) {
        UserRoleAssignment assignment = userRoleAssignmentRepository.findByIdAndDeletedAtIsNull(assignmentId)
            .filter(candidate -> candidate.getUserId().equals(userId))
            .orElseThrow(() -> new ResourceNotFoundException("ASSIGNMENT_NOT_FOUND", "Role assignment not found."));

        requireGrantAuthority(assignment.getScopeType(), assignment.getScopeId());

        if (SUPER_ADMIN.equals(roleCode(assignment.getRoleId()))
            && assignment.getScopeType() == ScopeType.GLOBAL
            && userRoleAssignmentRepository.countByRoleIdAndScopeTypeAndDeletedAtIsNull(assignment.getRoleId(), ScopeType.GLOBAL) <= 1) {
            throw new ConflictException("LAST_SUPER_ADMIN", "The final global SUPER_ADMIN assignment cannot be removed.");
        }

        assignment.markDeleted();
    }

    private void validateScope(Role role, ScopeType scopeType, UUID scopeId) {
        if ((scopeType == ScopeType.GLOBAL) != (scopeId == null)) {
            throw new ValidationException(
                "VALIDATION_FAILED",
                "scopeId must be null for GLOBAL scope and present for every other scope."
            );
        }
        if (role.getDefaultScopeType() != scopeType) {
            throw new ValidationException(
                "VALIDATION_FAILED",
                "Role " + role.getCode() + " may only be assigned at scope " + role.getDefaultScopeType() + "."
            );
        }
        if (scopeType == ScopeType.ORGANIZATION && organizationUnitRepository.findByIdAndDeletedAtIsNull(scopeId).isEmpty()) {
            throw new ResourceNotFoundException("SCOPE_ENTITY_NOT_FOUND", "Scope entity not found.");
        }
    }

    /** BR-URA-4: a grant may never reach beyond the caller's own {@code role:assign} scope. */
    private void requireGrantAuthority(ScopeType scopeType, UUID scopeId) {
        if (!scopeEvaluator.hasPermission(currentUser.requireUserId(), "role:assign", new ScopeTarget(scopeType, scopeId))) {
            throw new ScopeAccessDeniedException("SCOPE_FORBIDDEN", "Cannot grant or revoke a role beyond your own scope.");
        }
    }

    private String roleCode(UUID roleId) {
        return roleRepository.findById(roleId)
            .map(Role::getCode)
            .orElseThrow(() -> new ResourceNotFoundException("ROLE_NOT_FOUND", "Role not found."));
    }

    private RoleAssignmentResponse toResponse(UserRoleAssignment assignment, String roleCode) {
        return new RoleAssignmentResponse(
            assignment.getId(),
            assignment.getUserId(),
            roleCode,
            assignment.getScopeType(),
            assignment.getScopeId()
        );
    }
}
