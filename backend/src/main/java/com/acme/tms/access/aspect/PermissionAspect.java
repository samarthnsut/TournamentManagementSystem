package com.acme.tms.access.aspect;

import com.acme.tms.access.annotation.RequiresPermission;
import com.acme.tms.access.domain.ScopeType;
import com.acme.tms.access.service.CurrentUser;
import com.acme.tms.access.service.ScopeEvaluator;
import com.acme.tms.access.service.ScopeTarget;
import com.acme.tms.common.exception.ScopeAccessDeniedException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Aspect
@Component
@Order(1)
public class PermissionAspect {

    private final ScopeEvaluator scopeEvaluator;
    private final CurrentUser currentUser;

    public PermissionAspect(ScopeEvaluator scopeEvaluator, CurrentUser currentUser) {
        this.scopeEvaluator = scopeEvaluator;
        this.currentUser = currentUser;
    }

    @Around("@annotation(requiresPermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) throws Throwable {
        UUID userId = currentUser.requireUserId();
        ScopeTarget target = new ScopeTarget(requiresPermission.scope(), resolveScopeId(joinPoint, requiresPermission));

        if (!scopeEvaluator.hasPermission(userId, requiresPermission.value(), target)) {
            throw new ScopeAccessDeniedException(
                "SCOPE_FORBIDDEN",
                "Missing permission " + requiresPermission.value() + " at the requested scope."
            );
        }

        return joinPoint.proceed();
    }

    private UUID resolveScopeId(ProceedingJoinPoint joinPoint, RequiresPermission requiresPermission) {
        if (requiresPermission.scope() == ScopeType.GLOBAL || requiresPermission.scopeIdParam().isBlank()) {
            return null;
        }

        String[] path = requiresPermission.scopeIdParam().split("\\.");
        Object value = argumentNamed(joinPoint, path[0], requiresPermission.scopeIdParam());

        for (int segment = 1; segment < path.length && value != null; segment++) {
            value = accessorValue(value, path[segment], requiresPermission.scopeIdParam());
        }

        return (UUID) value;
    }

    private Object argumentNamed(ProceedingJoinPoint joinPoint, String name, String declaredPath) {
        String[] parameterNames = ((MethodSignature) joinPoint.getSignature()).getParameterNames();

        for (int index = 0; index < parameterNames.length; index++) {
            if (parameterNames[index].equals(name)) {
                return joinPoint.getArgs()[index];
            }
        }

        throw new IllegalStateException(
            "@RequiresPermission scopeIdParam '" + declaredPath + "' does not match any parameter of " + joinPoint.getSignature()
        );
    }

    private Object accessorValue(Object target, String accessor, String declaredPath) {
        try {
            return target.getClass().getMethod(accessor).invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                "@RequiresPermission scopeIdParam '" + declaredPath + "' is not resolvable on " + target.getClass(),
                exception
            );
        }
    }
}
