package com.acme.tms.organization.domain;

import com.acme.tms.common.domain.RegistrationApprovalPolicy;
import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "organization_unit")
public class OrganizationUnit extends SoftDeletableEntity {

    private UUID parentOrganizationUnitId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 120)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrganizationUnitType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationUnitStatus status = OrganizationUnitStatus.ACTIVE;

    /** The default a tournament inherits when it does not set its own. */
    @Enumerated(EnumType.STRING)
    @Column(name = "registration_approval_policy", nullable = false, length = 30)
    private RegistrationApprovalPolicy registrationApprovalPolicy =
        RegistrationApprovalPolicy.DIRECT_SINGLE_APPROVAL;

    public UUID getParentOrganizationUnitId() {
        return parentOrganizationUnitId;
    }

    public void setParentOrganizationUnitId(UUID parentOrganizationUnitId) {
        this.parentOrganizationUnitId = parentOrganizationUnitId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public OrganizationUnitType getType() {
        return type;
    }

    public void setType(OrganizationUnitType type) {
        this.type = type;
    }

    public RegistrationApprovalPolicy getRegistrationApprovalPolicy() {
        return registrationApprovalPolicy;
    }

    public void setRegistrationApprovalPolicy(RegistrationApprovalPolicy registrationApprovalPolicy) {
        this.registrationApprovalPolicy = registrationApprovalPolicy;
    }

    public OrganizationUnitStatus getStatus() {
        return status;
    }

    public void setStatus(OrganizationUnitStatus status) {
        this.status = status;
    }
}

