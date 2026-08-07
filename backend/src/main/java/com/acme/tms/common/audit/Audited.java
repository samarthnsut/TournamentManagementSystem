package com.acme.tms.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a mutating service method for the audit trail (ADR-014).
 *
 * <p>Lives in {@code common} beside {@link com.acme.tms.common.security.RequiresPermission} for the
 * same reason: every module needs to annotate, and none of them should have to depend on the audit
 * module to do it.
 *
 * <p>Coverage is not a matter of remembering — {@code AuditCoverageTest} fails the build when a
 * mutating service method carries neither this annotation nor an explicit exemption.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Audited {

    /** Business action, in the permission-code style: {@code tournament:publish}. */
    String value();

    /** Entity the action is about, matching an {@link AuditSnapshotProvider#entityType()}. */
    String entityType();

    /**
     * Method parameter holding the entity id, optionally dotted into a request record — the same
     * SpEL-lite form {@code RequiresPermission.scopeIdParam} uses.
     *
     * <p>Leave empty for a create, where the id does not exist until the method returns; the aspect
     * then reads it off the returned DTO's {@code id()}.
     */
    String entityIdParam() default "";
}
