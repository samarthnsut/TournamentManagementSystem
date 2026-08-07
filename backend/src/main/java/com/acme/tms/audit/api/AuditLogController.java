package com.acme.tms.audit.api;

import com.acme.tms.audit.dto.AuditLogResponse;
import com.acme.tms.audit.service.AuditLogService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Read-only by construction: there is no POST, PATCH or DELETE here and there never will be.
 *
 * <p>Deliberately no {@code @RequiresPermission}, matching the organization-unit list and the
 * approvals inbox. Both are scope-filtered reads where the answer is "what you can see", and
 * `audit:read` is held at ORGANIZATION by TENANT_ADMIN — a GLOBAL requirement would 403 exactly the
 * role this endpoint exists for. {@code AuditLogService} intersects the trail with the units the
 * caller's grant actually reaches, so a caller holding nothing gets an empty list rather than a
 * denial. Authentication is still required by SecurityConfig's {@code anyRequest().authenticated()}.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public List<AuditLogResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return auditLogService.list(limit);
    }

    @GetMapping("/entity")
    public List<AuditLogResponse> forEntity(
        @RequestParam String entityType,
        @RequestParam UUID entityId
    ) {
        return auditLogService.forEntity(entityType, entityId);
    }
}
