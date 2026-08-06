package com.acme.tms.workflow.domain;

import com.acme.tms.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/** An immutable decision record. Append-only: never updated, never deleted. */
@Entity
@Table(name = "approval_action")
public class ApprovalAction extends BaseEntity {

    @Column(nullable = false)
    private UUID instanceId;

    @Column(nullable = false)
    private int stepLevel;

    @Column(nullable = false)
    private UUID actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApprovalDecision decision;

    private String comment;

    public UUID getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(UUID instanceId) {
        this.instanceId = instanceId;
    }

    public int getStepLevel() {
        return stepLevel;
    }

    public void setStepLevel(int stepLevel) {
        this.stepLevel = stepLevel;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public ApprovalDecision getDecision() {
        return decision;
    }

    public void setDecision(ApprovalDecision decision) {
        this.decision = decision;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
