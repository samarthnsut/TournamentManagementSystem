package com.acme.tms.access.api;

import com.acme.tms.access.dto.CreateRoleAssignmentRequest;
import com.acme.tms.access.dto.RoleAssignmentResponse;
import com.acme.tms.access.service.RoleAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/role-assignments")
public class RoleAssignmentController {

    private final RoleAssignmentService roleAssignmentService;

    public RoleAssignmentController(RoleAssignmentService roleAssignmentService) {
        this.roleAssignmentService = roleAssignmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoleAssignmentResponse assign(
        @PathVariable UUID userId,
        @Valid @RequestBody CreateRoleAssignmentRequest request
    ) {
        return roleAssignmentService.assign(userId, request);
    }

    @GetMapping
    public List<RoleAssignmentResponse> list(@PathVariable UUID userId) {
        return roleAssignmentService.list(userId);
    }

    @DeleteMapping("/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID userId, @PathVariable UUID assignmentId) {
        roleAssignmentService.revoke(userId, assignmentId);
    }
}
