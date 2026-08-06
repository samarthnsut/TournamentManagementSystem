package com.acme.tms.workflow.domain;

import com.acme.tms.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "approval_step")
public class ApprovalStep extends SoftDeletableEntity {

    @Column(nullable = false)
    private UUID workflowId;

    @Column(nullable = false)
    private int level;

    /** Who may act here, intersected with scoped RBAC at action time (doc 07 section 6). */
    @Column(nullable = false, length = 50)
    private String roleCode;

    @Column(length = 150)
    private String stepName;

    /** False makes the level notify-only: the engine advances past it without waiting. */
    @Column(nullable = false)
    private boolean approvalRequired = true;

    public UUID getWorkflowId() {
        return workflowId;
    }

    public void setWorkflowId(UUID workflowId) {
        this.workflowId = workflowId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getStepName() {
        return stepName;
    }

    public void setStepName(String stepName) {
        this.stepName = stepName;
    }

    public boolean isApprovalRequired() {
        return approvalRequired;
    }

    public void setApprovalRequired(boolean approvalRequired) {
        this.approvalRequired = approvalRequired;
    }
}
