package com.acme.tms.document.domain;

import com.acme.tms.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * What the server signed, so that {@code attach} has something to check the client against.
 *
 * <p>Every field here was decided by the server at init time. At attach, none of it is re-read from
 * the request — a caller can only say "the upload you authorized is done", never "…and by the way
 * it belongs to this other tenant's registration".
 */
@Entity
@Table(name = "document_upload")
public class DocumentUpload extends BaseEntity {

    @Column(nullable = false)
    private UUID organizationUnitId;

    @Column(nullable = false, length = 50)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Column(nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 1024)
    private String objectKey;

    @Column(nullable = false, length = 120)
    private String mimeType;

    @Column(nullable = false)
    private long declaredSizeBytes;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant attachedAt;

    public boolean isAttached() {
        return attachedAt != null;
    }

    public boolean hasExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public UUID getOrganizationUnitId() {
        return organizationUnitId;
    }

    public void setOrganizationUnitId(UUID organizationUnitId) {
        this.organizationUnitId = organizationUnitId;
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

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public void setObjectKey(String objectKey) {
        this.objectKey = objectKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getDeclaredSizeBytes() {
        return declaredSizeBytes;
    }

    public void setDeclaredSizeBytes(long declaredSizeBytes) {
        this.declaredSizeBytes = declaredSizeBytes;
    }

    public UUID getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(UUID requestedBy) {
        this.requestedBy = requestedBy;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getAttachedAt() {
        return attachedAt;
    }

    public void setAttachedAt(Instant attachedAt) {
        this.attachedAt = attachedAt;
    }
}
