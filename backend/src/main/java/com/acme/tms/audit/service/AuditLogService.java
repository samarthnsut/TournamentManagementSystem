package com.acme.tms.audit.service;

import com.acme.tms.audit.domain.AuditLog;
import com.acme.tms.audit.dto.AuditLogResponse;
import com.acme.tms.audit.repository.AuditLogRepository;
import com.acme.tms.common.security.CurrentUser;
import com.acme.tms.common.security.ScopeEvaluator;
import com.acme.tms.common.security.ScopeTarget;
import com.acme.tms.identity.domain.User;
import com.acme.tms.identity.repository.UserRepository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Reads the trail. There is no write method here on purpose — rows are only ever created by
 * {@link AuditAspect}, and a service method that could author one by hand is the first step toward
 * a trail that can be curated.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public AuditLogService(
        AuditLogRepository auditLogRepository,
        ScopeEvaluator scopeEvaluator,
        CurrentUser currentUser,
        UserRepository userRepository,
        ObjectMapper objectMapper
    ) {
        this.auditLogRepository = auditLogRepository;
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Everything the caller may see, newest first.
     *
     * <p>Scoped by the units their {@code audit:read} grant actually reaches, not by the whole
     * table: an audit endpoint that ignored scope would be the most complete cross-tenant leak in
     * the product, since it carries snapshots of every other module's data.
     */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> list(int limit) {
        UUID userId = currentUser.requireUserId();
        PageRequest page = PageRequest.of(0, Math.clamp(limit, 1, 200),
            Sort.by(Sort.Direction.DESC, "timestamp"));

        // Some actions genuinely belong to no tenant — creating a root organization unit, or a
        // global role grant. Those rows resolve to a null owning unit, so a subtree filter would
        // hide them from everyone; only a caller who holds audit:read globally sees them.
        if (scopeEvaluator.hasPermission(userId, "audit:read", ScopeTarget.global())) {
            return toResponses(auditLogRepository.findAll(page).getContent());
        }

        List<UUID> visibleUnits = scopeEvaluator.visibleOrganizationUnitIds(userId, "audit:read");
        if (visibleUnits.isEmpty()) {
            return List.of();
        }

        return toResponses(auditLogRepository
            .findByOrganizationUnitIdInOrderByTimestampDesc(visibleUnits, PageRequest.of(0, Math.clamp(limit, 1, 200)))
            .getContent());
    }

    /** The history of one entity, for the "who changed this" question a screen asks. */
    @Transactional(readOnly = true)
    public List<AuditLogResponse> forEntity(String entityType, UUID entityId) {
        UUID userId = currentUser.requireUserId();
        List<AuditLog> all = auditLogRepository
            .findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId);

        if (scopeEvaluator.hasPermission(userId, "audit:read", ScopeTarget.global())) {
            return toResponses(all);
        }

        List<UUID> visibleUnits = scopeEvaluator.visibleOrganizationUnitIds(userId, "audit:read");
        List<AuditLog> rows = all.stream()
            // A platform-level row belongs to no subtree; the global branch above is the only way
            // to see one, so filtering it out here is the correct denial rather than an oversight.
            .filter(row -> row.getOrganizationUnitId() != null
                && visibleUnits.contains(row.getOrganizationUnitId()))
            .toList();

        return toResponses(rows);
    }

    private List<AuditLogResponse> toResponses(List<AuditLog> rows) {
        Set<UUID> actorIds = new LinkedHashSet<>();
        rows.forEach(row -> {
            if (row.getActorId() != null) {
                actorIds.add(row.getActorId());
            }
        });

        Map<UUID, String> actorNames = actorIds.isEmpty()
            ? Map.of()
            : userRepository.findAllById(actorIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, User::getFullName));

        return rows.stream()
            .map(row -> new AuditLogResponse(
                row.getId(),
                row.getActorId(),
                row.getActorId() == null ? "System" : actorNames.get(row.getActorId()),
                row.getAction(),
                row.getEntityType(),
                row.getEntityId(),
                readJson(row.getBeforeState()),
                readJson(row.getAfterState()),
                row.getOrganizationUnitId(),
                row.getIpAddress(),
                row.getTimestamp()
            ))
            .toList();
    }

    private Map<String, Object> readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            // A snapshot that cannot be read back must not hide the fact that the action happened.
            return Map.of("unreadableSnapshot", true);
        }
    }
}
