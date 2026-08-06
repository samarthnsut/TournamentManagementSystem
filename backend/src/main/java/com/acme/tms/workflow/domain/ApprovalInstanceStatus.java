package com.acme.tms.workflow.domain;

public enum ApprovalInstanceStatus {
    IN_PROGRESS,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean isOpen() {
        return this == IN_PROGRESS;
    }
}
