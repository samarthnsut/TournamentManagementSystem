package com.acme.tms.workflow.dto;

import com.acme.tms.workflow.domain.ApprovalDecision;

import java.time.Instant;
import java.util.UUID;

public record ApprovalActionResponse(
    UUID id,
    int stepLevel,
    UUID actorId,
    ApprovalDecision decision,
    String comment,
    Instant timestamp
) {
}
