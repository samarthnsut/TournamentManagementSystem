package com.acme.tms.identity.service;

import com.acme.tms.common.audit.Audited;
import com.acme.tms.common.exception.ConflictException;
import com.acme.tms.common.util.RandomTokenGenerator;
import com.acme.tms.common.util.Sha256;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.domain.UserStatus;
import com.acme.tms.identity.dto.InviteUserRequest;
import com.acme.tms.identity.dto.InviteUserResponse;
import com.acme.tms.identity.repository.UserRepository;
import com.acme.tms.organization.service.OrganizationUnitService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrganizationUnitService organizationUnitService;
    private final RoleAssignmentService roleAssignmentService;

    public UserService(
        UserRepository userRepository,
        OrganizationUnitService organizationUnitService,
        RoleAssignmentService roleAssignmentService
    ) {
        this.userRepository = userRepository;
        this.organizationUnitService = organizationUnitService;
        this.roleAssignmentService = roleAssignmentService;
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

        return new InviteUserResponse(
            user.getId(),
            user.getEmail(),
            user.getFullName(),
            user.getStatus(),
            request.organizationUnitId(),
            inviteToken
        );
    }
}

