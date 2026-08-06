package com.acme.tms.workflow.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WorkflowResponse(
    UUID id,
    UUID organizationUnitId,
    String workflowName,
    String entityType,
    boolean isActive,
    List<WorkflowStepResponse> steps,
    Instant createdAt
) {
}
