package com.acme.tms.workflow.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

/** A tenant's approval chain template. Resolution walks up the organization tree to find one. */
@Entity
@Table(name = "approval_workflow")
public class ApprovalWorkflow extends SoftDeletableEntity {

    @Column(nullable = false)
    private UUID organizationUnitId;

    @Column(nullable = false, length = 150)
    private String workflowName;

    @Column(nullable = false, length = 50)
    private String entityType;

    @Column(nullable = false)
    private boolean isActive = true;

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
    }

    public String getWorkflowName() {
        return workflowName;
    }

    public void setWorkflowName(String workflowName) {
        this.workflowName = workflowName;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }
}
