package com.acme.tms.workflow.dto;

import java.util.UUID;

public record WorkflowStepResponse(
    UUID id,
    int level,
    String roleCode,
    String stepName,
    boolean approvalRequired
) {
}
