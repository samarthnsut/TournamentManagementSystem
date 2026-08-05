package com.acme.tms.common.security;


import java.util.UUID;

public record ScopeTarget(ScopeType scopeType, UUID scopeId) {

    public static ScopeTarget global() {
        return new ScopeTarget(ScopeType.GLOBAL, null);
    }

    public static ScopeTarget organization(UUID organizationUnitId) {
        return new ScopeTarget(ScopeType.ORGANIZATION, organizationUnitId);
    }
}
