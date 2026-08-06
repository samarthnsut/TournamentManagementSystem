package com.acme.tms.workflow.dto;

import com.acme.tms.workflow.domain.ApprovalInstanceStatus;

import java.util.List;
import java.util.UUID;

/**
 * Answers "where is this stuck and who acts next" — the questions doc 07 section 5 says belong to
 * workflow state rather than the registration's own status.
 */
public record ApprovalInstanceResponse(
    UUID id,
    String entityType,
    UUID entityId,
    ApprovalInstanceStatus status,
    int currentLevel,
    int totalLevels,
    String currentStepName,
    String currentStepRole,
    /** e.g. "Awaiting State Approval (2 of 3)" — ready to render. */
    String progressLabel,
    List<ApprovalActionResponse> actions
) {
}
