package com.acme.tms.audit.domain;

import com.acme.tms.common.util.UuidV7Generator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * One recorded mutation. Deliberately not a {@code BaseEntity}: audit rows are append-only, so
 * created/updated columns and a soft-delete flag would all be either meaningless or a lie. There is
 * no setter-driven update path and the repository exposes no delete.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    /** Null for system actions — an auto-approval has no human behind it. */
    @Column(updatable = false)
    private UUID actorId;

    @Column(nullable = false, updatable = false, length = 80)
    private String action;

    @Column(nullable = false, updatable = false, length = 50)
    private String entityType;

    @Column(nullable = false, updatable = false)
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(updatable = false, columnDefinition = "jsonb")
    private String afterState;

    @Column(updatable = false)
    private UUID organizationUnitId;

    @Column(updatable = false, length = 45)
    private String ipAddress;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UuidV7Generator.generate();
        }
        if (timestamp == null) {
            timestamp = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID actorId) {
        this.actorId = actorId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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

    public String getBeforeState() {
        return beforeState;
    }

    public void setBeforeState(String beforeState) {
        this.beforeState = beforeState;
    }

    public String getAfterState() {
        return afterState;
    }

    public void setAfterState(String afterState) {
        this.afterState = afterState;
    }

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
