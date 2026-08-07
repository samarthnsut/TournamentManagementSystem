package com.acme.tms.workflow.service;

import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.common.exception.ResourceNotFoundException;

import com.acme.tms.workflow.repository.ApprovalWorkflowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Who may approve what is a tenant's own policy; changes to it belong in the record. */
/*
 * Providers must not throw. A ResourceNotFoundException raised inside a nested @Transactional
 * service method marks the caller's transaction rollback-only *before* the audit aspect can catch
 * it, which would take down the very operation being recorded. Existence is therefore checked
 * against the repository first, and the throwing read is only reached when it cannot throw.
 */
@Component
public class ApprovalWorkflowAuditSnapshots implements AuditSnapshotProvider {

    public static final String APPROVAL_WORKFLOW = "ApprovalWorkflow";

    private final ApprovalWorkflowService approvalWorkflowService;
    private final ApprovalWorkflowRepository approvalWorkflowRepository;

    public ApprovalWorkflowAuditSnapshots(ApprovalWorkflowService approvalWorkflowService, ApprovalWorkflowRepository approvalWorkflowRepository) {
        this.approvalWorkflowService = approvalWorkflowService;
        this.approvalWorkflowRepository = approvalWorkflowRepository;
    }

    @Override
    public String entityType() {
        return APPROVAL_WORKFLOW;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Object> snapshot(UUID entityId) {
        if (approvalWorkflowRepository.findByIdAndDeletedAtIsNull(entityId).isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(approvalWorkflowService.get(entityId));
        } catch (ResourceNotFoundException exception) {
            return Optional.empty();
        }
    }
}
