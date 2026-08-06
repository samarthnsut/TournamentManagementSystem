package com.acme.tms.registration.api;

import com.acme.tms.registration.service.RegistrationApprovalService;
import com.acme.tms.workflow.dto.ApprovalActionRequest;
import com.acme.tms.workflow.dto.ApprovalInstanceResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Deciding on an entry. Scope is checked in the service against the competition the entry belongs
 * to, because the registration id alone does not tell the aspect which scope to test.
 */
@RestController
@RequestMapping("/api/v1")
public class RegistrationApprovalController {

    private final RegistrationApprovalService approvalService;

    public RegistrationApprovalController(RegistrationApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    /** Everything waiting on this caller, across every organization they can reach. */
    @GetMapping("/approvals/inbox")
    public List<RegistrationApprovalService.InboxItem> inbox() {
        return approvalService.inbox();
    }

    @GetMapping("/registrations/{registrationId}/approval")
    public ApprovalInstanceResponse forRegistration(@PathVariable UUID registrationId) {
        return approvalService.forRegistration(registrationId);
    }

    @PostMapping("/registrations/{registrationId}/approve")
    public ApprovalInstanceResponse approve(
        @PathVariable UUID registrationId,
        @RequestBody(required = false) ApprovalActionRequest request
    ) {
        return approvalService.approve(registrationId, request == null ? null : request.comment());
    }

    @PostMapping("/registrations/{registrationId}/reject")
    public ApprovalInstanceResponse reject(
        @PathVariable UUID registrationId,
        @Valid @RequestBody ApprovalActionRequest request
    ) {
        return approvalService.reject(registrationId, request.comment());
    }
}
