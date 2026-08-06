package com.acme.tms.workflow.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.UUID;

/** One entity moving through one chain. Null {@code workflowId} means the implicit single step. */
@Entity
@Table(name = "approval_instance")
public class ApprovalInstance extends SoftDeletableEntity {

    @Column(nullable = false)
    private UUID organizationUnitId;

    private UUID workflowId;

    @Column(nullable = false, length = 50)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false)
    private int currentLevel = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalInstanceStatus status = ApprovalInstanceStatus.IN_PROGRESS;

    /**
     * Two approvers acting on the same level at once must not both advance it. The loser of the
     * race gets an optimistic-locking failure, which the service reports as a conflict.
     */
    @Version
    private Long lockVersion;

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
    }

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID entityId) {
        this.entityId = entityId;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public void setCurrentLevel(int currentLevel) {
        this.currentLevel = currentLevel;
    }

    public ApprovalInstanceStatus getStatus() {
        return status;
    }

    public void setStatus(ApprovalInstanceStatus status) {
        this.status = status;
    }

    public Long getLockVersion() {
        return lockVersion;
    }
}
