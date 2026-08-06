package com.acme.tms.workflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record WorkflowStepRequest(
    @Positive int level,
    @NotBlank String roleCode,
    String stepName,
    Boolean approvalRequired
) {
}
