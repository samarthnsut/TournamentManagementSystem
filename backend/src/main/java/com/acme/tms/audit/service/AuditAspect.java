package com.acme.tms.audit.service;

import com.acme.tms.audit.domain.AuditLog;
import com.acme.tms.audit.repository.AuditLogRepository;
import com.acme.tms.common.audit.AuditSnapshotProvider;
import com.acme.tms.common.audit.Audited;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes an {@link AuditLog} row for every {@link Audited} service method (ADR-014).
 *
 * <p>Ordered after {@code PermissionAspect}: a call that is about to be refused with a 403 has not
 * happened, and recording it as though it had would make the trail actively misleading.
 *
 * <p>The row is written inside the caller's transaction. An audit row without its mutation — or a
 * mutation without its row — is worse than the extra write, which is the trade ADR-014 accepts.
 * The corollary is that a failure *here* must never take down the business operation, so anything
 * that goes wrong after the method has succeeded is logged loudly and swallowed.
 */
@Aspect
@Component
@Order(2)
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);

    /** Matches {@code OrganizationUnitAuditSnapshots.ORGANIZATION_UNIT}, kept literal so the audit module depends on no other. */
    private static final String ORGANIZATION_UNIT_ENTITY = "OrganizationUnit";

    private final AuditLogRepository auditLogRepository;
    private final AuditRedactor redactor;
    private final AuditContext auditContext;
    private final Map<String, AuditSnapshotProvider> providers;

    public AuditAspect(
        AuditLogRepository auditLogRepository,
        AuditRedactor redactor,
        AuditContext auditContext,
        List<AuditSnapshotProvider> snapshotProviders
    ) {
        this.auditLogRepository = auditLogRepository;
        this.redactor = redactor;
        this.auditContext = auditContext;

        Map<String, AuditSnapshotProvider> byType = new HashMap<>();
        for (AuditSnapshotProvider provider : snapshotProviders) {
            AuditSnapshotProvider clash = byType.put(provider.entityType(), provider);
            if (clash != null) {
                // Two providers for one type would make the recorded snapshot depend on bean order.
                throw new IllegalStateException(
                    "Duplicate AuditSnapshotProvider for " + provider.entityType() + ": "
                        + clash.getClass().getName() + " and " + provider.getClass().getName()
                );
            }
        }
        this.providers = Map.copyOf(byType);
    }

    @Around("@annotation(audited)")
    public Object record(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        AuditSnapshotProvider provider = providers.get(audited.entityType());

        UUID entityIdBefore = resolveEntityId(joinPoint, audited);
        Object before = snapshot(provider, entityIdBefore);

        Object result = joinPoint.proceed();

        try {
            // A create has no id until now; read it off whatever came back.
            UUID entityId = entityIdBefore != null ? entityIdBefore : idOf(result);
            if (entityId == null) {
                log.warn("Audited method {} produced no entity id; no audit row written",
                    joinPoint.getSignature().toShortString());
                return result;
            }

            Object after = snapshot(provider, entityId);

            AuditLog entry = new AuditLog();
            entry.setAction(audited.value());
            entry.setEntityType(audited.entityType());
            entry.setEntityId(entityId);
            entry.setActorId(auditContext.actorId().orElse(null));
            entry.setIpAddress(auditContext.ipAddress().orElse(null));
            entry.setBeforeState(redactor.toRedactedJson(before));
            entry.setAfterState(redactor.toRedactedJson(after));
            entry.setOrganizationUnitId(
                resolveOrganizationUnit(joinPoint, audited, entityId, after, before));
            auditLogRepository.save(entry);
        } catch (RuntimeException exception) {
            // The mutation already happened and was legitimate. Failing the call now would turn an
            // audit defect into a user-visible outage and, worse, roll the mutation back.
            log.error("Failed to write audit row for {}", joinPoint.getSignature().toShortString(), exception);
        }

        return result;
    }

    private Object snapshot(AuditSnapshotProvider provider, UUID entityId) {
        if (provider == null || entityId == null) {
            return null;
        }
        try {
            return provider.snapshot(entityId).orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Audit snapshot failed for {} {}", provider.entityType(), entityId, exception);
            return null;
        }
    }

    /** Null when the annotation names no parameter — the create case. */
    private UUID resolveEntityId(ProceedingJoinPoint joinPoint, Audited audited) {
        if (audited.entityIdParam().isBlank()) {
            return null;
        }

        String[] path = audited.entityIdParam().split("\\.");
        Object value = argumentNamed(joinPoint, path[0], audited.entityIdParam());
        for (int segment = 1; segment < path.length && value != null; segment++) {
            value = accessorValue(value, path[segment], audited.entityIdParam());
        }
        return value instanceof UUID id ? id : null;
    }

    private Object argumentNamed(ProceedingJoinPoint joinPoint, String name, String declaredPath) {
        String[] parameterNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        for (int index = 0; index < parameterNames.length; index++) {
            if (parameterNames[index].equals(name)) {
                return joinPoint.getArgs()[index];
            }
        }
        throw new IllegalStateException(
            "@Audited entityIdParam '" + declaredPath + "' does not match any parameter of "
                + joinPoint.getSignature());
    }

    private Object accessorValue(Object target, String accessor, String declaredPath) {
        try {
            return target.getClass().getMethod(accessor).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "@Audited entityIdParam '" + declaredPath + "' is not resolvable on " + target.getClass(),
                exception);
        }
    }

    private UUID idOf(Object result) {
        return readUuid(result, "id");
    }

    /**
     * Which tenant this row belongs to. It matters more than it looks: the audit read path filters
     * by owning unit, so a row that resolves to null is one nobody but a global reader will ever
     * see. Three sources, in order of trustworthiness.
     */
    private UUID resolveOrganizationUnit(
        ProceedingJoinPoint joinPoint,
        Audited audited,
        UUID entityId,
        Object after,
        Object before
    ) {
        // 1. Denormalized onto most responses.
        UUID fromSnapshot = readUuid(after, "organizationUnitId");
        if (fromSnapshot == null) {
            fromSnapshot = readUuid(before, "organizationUnitId");
        }
        if (fromSnapshot != null) {
            return fromSnapshot;
        }

        // 2. An OrganizationUnit does not carry its own id under that name — it is the unit.
        if (ORGANIZATION_UNIT_ENTITY.equals(audited.entityType())) {
            return entityId;
        }

        // 3. A create whose snapshot is thin often still names the unit in its request.
        for (Object argument : joinPoint.getArgs()) {
            UUID fromArgument = readUuid(argument, "organizationUnitId");
            if (fromArgument != null) {
                return fromArgument;
            }
        }

        return null;
    }

    private UUID readUuid(Object target, String accessor) {
        if (target == null) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(accessor).invoke(target);
            return value instanceof UUID id ? id : null;
        } catch (ReflectiveOperationException exception) {
            // Not every snapshot carries every accessor, and that is not an error.
            return null;
        }
    }
}
