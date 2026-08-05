package com.acme.tms.identity.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;
import com.acme.tms.common.security.ScopeType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "role")
public class Role extends SoftDeletableEntity {

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScopeType defaultScopeType;

    @Column(name = "is_system_role", nullable = false)
    private boolean systemRole = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ScopeType getDefaultScopeType() {
        return defaultScopeType;
    }

    public void setDefaultScopeType(ScopeType defaultScopeType) {
        this.defaultScopeType = defaultScopeType;
    }

    public boolean isSystemRole() {
        return systemRole;
    }

    public void setSystemRole(boolean systemRole) {
        this.systemRole = systemRole;
    }
}
