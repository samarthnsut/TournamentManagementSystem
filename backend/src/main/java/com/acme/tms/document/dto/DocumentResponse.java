package com.acme.tms.document.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param fileUrl the storage object key, stable forever
 * @param downloadUrl a short-lived signed URL; it expires, so it is never persisted anywhere
 */
public record DocumentResponse(
    UUID id,
    UUID organizationUnitId,
    String entityType,
    UUID entityId,
    String fileName,
    String fileUrl,
    String mimeType,
    long sizeBytes,
    UUID uploadedBy,
    Instant createdAt,
    String downloadUrl
) {
}
