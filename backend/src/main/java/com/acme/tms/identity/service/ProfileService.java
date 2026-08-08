package com.acme.tms.identity.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.exception.ValidationException;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.identity.domain.RefreshToken;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.dto.ChangePasswordRequest;
import com.acme.tms.identity.dto.ProfileResponse;
import com.acme.tms.identity.dto.UpdateProfileRequest;
import com.acme.tms.identity.repository.RefreshTokenRepository;
import com.acme.tms.identity.repository.UserRepository;
import com.acme.tms.identity.repository.UserRoleAssignmentRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The signed-in user acting on their own account.
 *
 * <p>Everything here is implicitly scoped to the caller — there is no id parameter to pass, so
 * there is no id parameter to tamper with. That is why these live apart from {@code UserService},
 * which is about administering *other* people and is permission-gated accordingly.
 */
@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final RoleAssignmentService roleAssignmentService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    public ProfileService(
        UserRepository userRepository,
        UserRoleAssignmentRepository userRoleAssignmentRepository,
        RefreshTokenRepository refreshTokenRepository,
        RoleAssignmentService roleAssignmentService,
        PasswordEncoder passwordEncoder,
        CurrentUser currentUser
    ) {
        this.userRepository = userRepository;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.roleAssignmentService = roleAssignmentService;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get() {
        User user = require(currentUser.requireUserId());

        List<String> permissions = userRoleAssignmentRepository.findScopedPermissions(user.getId())
            .stream()
            .map(UserRoleAssignmentRepository.ScopedPermissionRow::getPermissionCode)
            .distinct()
            .sorted()
            .toList();

        return new ProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getPhone(),
            user.getStatus(),
            user.getCreatedAt(),
            roleAssignmentService.list(user.getId()),
            permissions
        );
    }

    @Transactional
    @Audited(value = "user:update-profile", entityType = "User")
    public ProfileResponse update(UpdateProfileRequest request) {
        User user = require(currentUser.requireUserId());
        user.setFullName(request.fullName().trim());
        // Blank clears it; a stored empty string would be a phone number nobody can call.
        user.setPhone(
            request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        return get();
    }

    /**
     * Changes the password and ends every other session.
     *
     * <p>Someone changing their password has usually just decided somebody else might know the old
     * one. Leaving other refresh tokens alive would mean the change did nothing about the case it
     * was made for. The caller's own session goes too — they sign in again, which is the honest
     * outcome and the one every other product does.
     */
    @Transactional
    @Audited(value = "user:change-password", entityType = "User")
    public void changePassword(ChangePasswordRequest request) {
        User user = require(currentUser.requireUserId());

        if (user.getPasswordHash() == null
            || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            // Deliberately not "wrong password for this account" — same wording whatever the cause.
            throw new ValidationException("INVALID_CREDENTIALS", "The current password is not correct.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new ValidationException(
                "PASSWORD_UNCHANGED", "The new password must be different from the current one.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));

        for (RefreshToken token : refreshTokenRepository.findByUserIdAndRevokedAtIsNull(user.getId())) {
            token.revoke();
        }
    }

    private User require(UUID userId) {
        return userRepository.findById(userId)
            .filter(candidate -> candidate.getDeletedAt() == null)
            .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found."));
    }
}
