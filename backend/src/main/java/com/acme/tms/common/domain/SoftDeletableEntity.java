package com.acme.tms.common.domain;

import jakarta.persistence.MappedSuperclass;

import java.time.Instant;

@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    private Instant deletedAt;

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted() {
        deletedAt = Instant.now();
    }
}

