package com.acme.tms.audit.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * @param actorId null for system actions — an auto-approval has no human behind it
 * @param beforeState null for a create; {@code afterState} is null for a delete
 */
public record AuditLogResponse(
    UUID id,
    UUID actorId,
    String actorName,
    String action,
    String entityType,
    UUID entityId,
    Map<String, Object> beforeState,
    Map<String, Object> afterState,
    UUID organizationUnitId,
    String ipAddress,
    Instant timestamp
) {
}
