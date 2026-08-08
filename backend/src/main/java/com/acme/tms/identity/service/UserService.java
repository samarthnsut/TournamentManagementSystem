package com.acme.tms.identity.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.util.RandomTokenGenerator;
import com.acme.tms.common.util.Sha256;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.domain.UserStatus;
import com.acme.tms.identity.dto.InviteUserRequest;
import com.acme.tms.identity.dto.InviteUserResponse;
import com.acme.tms.identity.dto.UserListItemResponse;
import com.acme.tms.identity.repository.UserRepository;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.organization.service.OrganizationUnitService;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationUnitService organizationUnitService;
    private final RoleAssignmentService roleAssignmentService;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;
    private final ApplicationEventPublisher events;

    public UserService(
        UserRepository userRepository,
        OrganizationUnitService organizationUnitService,
        RoleAssignmentService roleAssignmentService,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser,
        ApplicationEventPublisher events
    ) {
        this.userRepository = userRepository;
        this.organizationUnitService = organizationUnitService;
        this.roleAssignmentService = roleAssignmentService;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
        this.events = events;
    }

    /**
     * The directory a role-management screen lists. Scoped to the units the caller can reach with
     * {@code user:read} — an unscoped user list would enumerate every tenant on the platform.
     */
    @Transactional(readOnly = true)
    public List<UserListItemResponse> list() {
        List<UUID> visibleUnits =
            scopeEvaluator.visibleOrganizationUnitIds(currentUser.requireUserId(), "user:read");

        if (visibleUnits.isEmpty()) {
            return List.of();
        }

        return userRepository.findInOrganizationUnits(visibleUnits).stream()
            .map(user -> new UserListItemResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                user.getCreatedAt(),
                roleAssignmentService.list(user.getId())))
            .toList();
    }

    @Transactional
    @Audited(value = "user:invite", entityType = "User")
    public InviteUserResponse invite(InviteUserRequest request) {
        organizationUnitService.findActiveUnit(request.organizationUnitId());

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(email)) {
            throw new ConflictException("EMAIL_ALREADY_REGISTERED", "A user with this email already exists.");
        }

        String inviteToken = RandomTokenGenerator.generate();
        User user = new User();
        user.setEmail(email);
        user.setFullName(request.displayName().trim());
        user.setStatus(UserStatus.INVITED);
        user.setInviteTokenHash(Sha256.hash(inviteToken));
        user.setInviteExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        user = userRepository.save(user);

        if (request.initialRole() != null) {
            roleAssignmentService.assign(user.getId(), request.initialRole());
        }

        // Delivered after this transaction commits, so a rolled-back invite never reaches an inbox.
        // The token is still returned below regardless: mail is best-effort, and an organizer with
        // no working SMTP has to be able to pass the invite on by hand.
        events.publishEvent(new UserInvitedEvent(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            inviteToken,
            inviterName()
        ));

        return new InviteUserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getStatus(),
            request.organizationUnitId(),
            inviteToken
        );
    }

    /**
     * Who to name in the invite email. Best-effort by design: this is a courtesy line in a message
     * body, and failing to resolve it must never be the reason an invite is not created.
     */
    private String inviterName() {
        try {
            return userRepository.findById(currentUser.requireUserId())
                .map(User::getFullName)
                .orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}

