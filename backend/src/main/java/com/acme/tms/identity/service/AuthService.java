package com.acme.tms.identity.service;

import com.acme.tms.access.domain.ScopeType;
import com.acme.tms.access.service.RoleAssignmentService;
import com.acme.tms.common.exception.AuthenticationException;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.exception.ResourceNotFoundException;
import com.acme.tms.common.util.Sha256;
import com.acme.tms.identity.domain.RefreshToken;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.domain.UserStatus;
import com.acme.tms.identity.dto.AcceptInviteRequest;
import com.acme.tms.identity.dto.BootstrapRegisterRequest;
import com.acme.tms.identity.dto.LoginRequest;
import com.acme.tms.identity.dto.TokenResponse;
import com.acme.tms.identity.dto.UserResponse;
import com.acme.tms.identity.repository.UserRepository;
import com.acme.tms.organization.domain.OrganizationUnitType;
import com.acme.tms.organization.dto.CreateOrganizationUnitRequest;
import com.acme.tms.organization.dto.OrganizationUnitResponse;
import com.acme.tms.organization.service.OrganizationUnitService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final OrganizationUnitService organizationUnitService;
    private final RoleAssignmentService roleAssignmentService;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        RefreshTokenService refreshTokenService,
        OrganizationUnitService organizationUnitService,
        RoleAssignmentService roleAssignmentService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.organizationUnitService = organizationUnitService;
        this.roleAssignmentService = roleAssignmentService;
    }

    @Transactional
    public TokenResponse bootstrapRegister(BootstrapRegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "A user with this email already exists.");
        }

        OrganizationUnitResponse organizationUnit = organizationUnitService.create(new CreateOrganizationUnitRequest(
            null,
            request.organizationName(),
            null,
            request.organizationType() == null ? OrganizationUnitType.PRIVATE_ORGANIZER : request.organizationType()
        ));

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user = userRepository.save(user);

        roleAssignmentService.grant(user.getId(), "TENANT_ADMIN", ScopeType.ORGANIZATION, organizationUnit.id());

        return tokenResponse(user, refreshTokenService.issue(user));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normalizeEmail(request.email()))
            .orElseThrow(() -> new AuthenticationException("INVALID_CREDENTIALS", "Invalid email or password."));

        if (user.getStatus() == UserStatus.SUSPENDED || user.getStatus() == UserStatus.DEACTIVATED) {
            throw new com.acme.tms.common.exception.ScopeAccessDeniedException("USER_SUSPENDED", "User is not allowed to authenticate.");
        }
        if (user.getStatus() == UserStatus.INVITED || user.getPasswordHash() == null) {
            throw new com.acme.tms.common.exception.ScopeAccessDeniedException("USER_INVITED", "Invite must be accepted before login.");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new AuthenticationException("INVALID_CREDENTIALS", "Invalid email or password.");
        }

        return tokenResponse(user, refreshTokenService.issue(user));
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken existingToken = refreshTokenService.requireActive(refreshToken);
        User user = userRepository.findById(existingToken.getUserId())
            .orElseThrow(() -> new AuthenticationException("REFRESH_TOKEN_INVALID", "Refresh token is invalid."));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AuthenticationException("REFRESH_TOKEN_INVALID", "Refresh token is invalid.");
        }

        return tokenResponse(user, refreshTokenService.rotate(refreshToken, user));
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.logout(refreshToken);
    }

    @Transactional
    public TokenResponse acceptInvite(AcceptInviteRequest request) {
        User user = userRepository.findByInviteTokenHashAndDeletedAtIsNull(Sha256.hash(request.inviteToken()))
            .orElseThrow(() -> new ResourceNotFoundException("INVITE_NOT_FOUND", "Invite token was not found."));

        if (user.getStatus() != UserStatus.INVITED) {
            throw new ConflictException("INVITE_ALREADY_ACCEPTED", "Invite has already been accepted.");
        }
        if (user.getInviteExpiresAt() == null || user.getInviteExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("INVITE_EXPIRED", "Invite token has expired.");
        }

        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setFullName(request.displayName().trim());
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setStatus(UserStatus.ACTIVE);
        user.setInviteTokenHash(null);
        user.setInviteExpiresAt(null);

        return tokenResponse(user, refreshTokenService.issue(user));
    }

    public UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getStatus());
    }

    private TokenResponse tokenResponse(User user, TokenPair tokenPair) {
        return new TokenResponse(
            tokenPair.accessToken(),
            tokenPair.refreshToken(),
            tokenPair.expiresIn(),
            toResponse(user)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

