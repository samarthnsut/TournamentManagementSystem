package com.acme.tms.document.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * @param presignedUrl the client PUTs the bytes here directly — they never pass through this
 *     application — then calls attach with {@code uploadId}
 */
public record InitUploadResponse(UUID uploadId, String presignedUrl, Instant expiresAt) {

    /** The audit aspect reads an entity id off {@code id()}; this is the same value. */
    public UUID id() {
        return uploadId;
    }
}
