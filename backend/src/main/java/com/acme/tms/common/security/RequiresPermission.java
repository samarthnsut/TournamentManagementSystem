package com.acme.tms.common.security;


import com.acme.tms.identity.domain.Permission;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RequiresPermission {

    /** Permission code from the seeded catalog, e.g. {@code tournament:create}. */
    String value();

    ScopeType scope();

    /**
     * Method parameter holding the scope entity id, optionally dotted into a request record —
     * {@code "id"} or {@code "request.organizationUnitId"}. Empty for GLOBAL scope.
     */
    String scopeIdParam() default "";
}
