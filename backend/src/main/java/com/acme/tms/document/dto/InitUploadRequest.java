package com.acme.tms.document.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record InitUploadRequest(
    @NotBlank @Size(max = 255) String fileName,
    @NotBlank @Size(max = 120) String mimeType,
    @Positive long sizeBytes,
    @NotBlank @Size(max = 50) String entityType,
    @NotNull UUID entityId
) {
}
