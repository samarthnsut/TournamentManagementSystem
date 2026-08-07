package com.acme.tms.identity.api;

import com.acme.tms.common.security.RequiresPermission;
import com.acme.tms.common.security.ScopeType;
import com.acme.tms.identity.dto.InviteUserRequest;
import com.acme.tms.identity.dto.InviteUserResponse;
import com.acme.tms.identity.dto.UserListItemResponse;
import com.acme.tms.identity.service.UserService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(value = "user:invite", scope = ScopeType.ORGANIZATION, scopeIdParam = "request.organizationUnitId")
    public InviteUserResponse invite(@Valid @RequestBody InviteUserRequest request) {
        return userService.invite(request);
    }

    /**
     * Scope-filtered rather than permission-gated, like the organization-unit list: the answer is
     * "the people you can administer", and a caller with no reach gets an empty list.
     */
    @GetMapping
    public java.util.List<UserListItemResponse> list() {
        return userService.list();
    }
}
