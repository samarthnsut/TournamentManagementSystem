package com.acme.tms.identity.api;

import com.acme.tms.common.security.AuthenticatedUser;
import com.acme.tms.identity.dto.AcceptInviteRequest;
import com.acme.tms.identity.dto.BootstrapRegisterRequest;
import com.acme.tms.identity.dto.LoginRequest;
import com.acme.tms.identity.dto.LogoutRequest;
import com.acme.tms.identity.dto.RefreshRequest;
import com.acme.tms.identity.dto.TokenResponse;
import com.acme.tms.identity.repository.UserRoleAssignmentRepository;
import com.acme.tms.identity.service.AuthService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRoleAssignmentRepository userRoleAssignmentRepository;

    public AuthController(
        AuthService authService,
        UserRoleAssignmentRepository userRoleAssignmentRepository
    ) {
        this.authService = authService;
        this.userRoleAssignmentRepository = userRoleAssignmentRepository;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse register(@Valid @RequestBody BootstrapRegisterRequest request) {
        return authService.bootstrapRegister(request);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }

    @PostMapping("/invite-accept")
    public TokenResponse acceptInvite(@Valid @RequestBody AcceptInviteRequest request) {
        return authService.acceptInvite(request);
    }

    /**
     * Includes the caller's permission and role codes so the UI can hide actions they cannot take.
     * The API still enforces every one of them — this only stops us offering a button that is
     * guaranteed to fail.
     */
    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AuthenticatedUser user) {
        List<String> permissions = userRoleAssignmentRepository.findScopedPermissions(user.userId())
            .stream()
            .map(UserRoleAssignmentRepository.ScopedPermissionRow::getPermissionCode)
            .distinct()
            .sorted()
            .toList();

        return Map.of(
            "id", user.userId(),
            "email", user.email(),
            "permissions", permissions,
            "roles", userRoleAssignmentRepository.findRoleCodes(user.userId())
        );
    }
}

